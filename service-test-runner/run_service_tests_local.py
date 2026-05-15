#!/usr/bin/env python3
"""Run mongodb-developer/service-tests compatibility checks locally.

This wraps the repository's Python unittest compatibility suite so it can be
run without the original Atlas-backed result database.
"""

from __future__ import annotations

import argparse
import inspect
import json
import os
import re
import subprocess
import sys
import time
import traceback
import unittest
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List


PAGE_MODULES = [
    "test_all_operators",
    "test_data_types",
    "test_aggregation",
    "test_change_streams",
    "test_transactions",
    "test_schema_validation",
    "test_indexing",
]

SAFE_EXTRA_MODULES = [
    "test_error_handling",
    "test_geospatial_queries",
    "test_search",
    "test_timeseries",
    "test_diagnostic_commands",
    "test_system_collections",
]

SKIPPED_BY_DEFAULT = {
    "test_administrative_commands": "mutates cluster/admin state, including global write-block command",
    "test_replication_commands": "contains replica set initiate/reconfig/stepdown commands",
    "test_role_management_commands": "creates, updates, and drops roles",
    "test_user_management_commands": "creates, authenticates, updates, and drops admin users",
    "test_sessions_commands": "contains killAllSessions-style global session commands",
    "test_field_level_encryption": "depends on client-side encryption runtime, not only server API behavior",
    "test_mongodb_tools": "depends on external mongodump/mongorestore paths",
    "test_retryable_writes": "uses a separate local BaseTest that writes to a result database",
}

CATEGORY_BY_MODULE = {
    "test_all_operators": "Core CRUD / Operators",
    "test_indexing": "Core CRUD / Indexes",
    "test_data_types": "BSON / Decimal / Data Types",
    "test_aggregation": "Aggregation",
    "test_change_streams": "Change Streams",
    "test_transactions": "Transactions",
    "test_schema_validation": "JSON Schema Validation",
    "test_error_handling": "Error Handling",
    "test_geospatial_queries": "Geospatial",
    "test_search": "Search",
    "test_timeseries": "Time Series",
    "test_diagnostic_commands": "Diagnostic Commands",
    "test_system_collections": "System Collections",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--uri", default=os.environ.get("MONGODB_COMPAT_URI"), help="MongoDB-compatible URI")
    parser.add_argument(
        "--service-tests-dir",
        default="/tmp/mongodb-service-tests-study/Compatibility Websites Code",
        help="Path to service-tests 'Compatibility Websites Code' directory",
    )
    parser.add_argument("--db-name", default=None, help="Database name used for destructive test collections")
    parser.add_argument("--output-dir", default=None, help="Directory for JSON, logs, and Markdown report")
    parser.add_argument("--timeout", type=int, default=90, help="Timeout per test module in seconds")
    parser.add_argument(
        "--profile",
        choices=["page", "extended-safe"],
        default="extended-safe",
        help="page runs the MongoDB article-like API areas; extended-safe adds non-mutating extras",
    )
    parser.add_argument("--modules", default="", help="Comma-separated module override, e.g. test_aggregation,test_indexing")
    parser.add_argument("--child-module", default="", help=argparse.SUPPRESS)
    parser.add_argument("--child-output", default="", help=argparse.SUPPRESS)
    return parser.parse_args()


def uri_with_timeouts(uri: str) -> str:
    timeout_options = "serverSelectionTimeoutMS=3000&connectTimeoutMS=3000&socketTimeoutMS=10000"
    separator = "&" if "?" in uri else "?"
    return f"{uri}{separator}{timeout_options}"


def redact_uri(uri: str) -> str:
    return re.sub(r"(mongodb(?:\+srv)?://[^:/@]+):([^@]+)@", r"\1:****@", uri)


def module_list(args: argparse.Namespace) -> List[str]:
    if args.modules.strip():
        return [item.strip() for item in args.modules.split(",") if item.strip()]
    if args.profile == "page":
        return list(PAGE_MODULES)
    return list(PAGE_MODULES + SAFE_EXTRA_MODULES)


def now_stamp() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def status_of(record: Dict[str, Any]) -> str:
    return str(record.get("status", "")).lower()


def normalize_record(record: Dict[str, Any], module_name: str) -> Dict[str, Any]:
    out = dict(record)
    out.setdefault("module", module_name)
    out.setdefault("suite", module_name)
    out.setdefault("test_name", out.get("test_file", module_name))
    out.setdefault("reason", "PASSED" if status_of(out) == "pass" else "FAILED")
    out["category"] = CATEGORY_BY_MODULE.get(module_name, module_name)
    return out


def child_main(args: argparse.Namespace) -> int:
    service_tests_dir = Path(args.service_tests_dir).resolve()
    tests_dir = service_tests_dir / "tests"
    sys.path.insert(0, str(service_tests_dir))
    sys.path.insert(0, str(tests_dir))

    import config  # type: ignore
    import base_test  # type: ignore

    config.DOCDB_URI = uri_with_timeouts(args.uri)
    config.DOCDB_DB_NAME = args.db_name
    config.RESULT_DB_URI = config.DOCDB_URI
    config.RESULT_DB_NAME = f"{args.db_name}_compat_results"
    config.RESULT_COLLECTION_NAME = "correctness"
    config.PLATFORM = "Local MongoDB-compatible service"

    def local_teardown(cls: type) -> None:
        try:
            client = getattr(cls, "docdb_client", None)
            if client is not None:
                client.close()
        except Exception:
            pass

    base_test.BaseTest.tearDownClass = classmethod(local_teardown)

    setup_records: List[Dict[str, Any]] = []
    if args.child_module == "test_all_operators":
        from pymongo.synchronous.collection import Collection

        original_create_index = Collection.create_index

        def create_index_with_setup_tolerance(self: Any, keys: Any, *call_args: Any, **call_kwargs: Any) -> Any:
            try:
                return original_create_index(self, keys, *call_args, **call_kwargs)
            except Exception as exc:
                if self.name == "test_all_operators" and "2dsphere" in str(keys):
                    setup_records.append(
                        normalize_record(
                            {
                                "status": "fail",
                                "test_name": "Operator Test Setup - 2dsphere index",
                                "suite": args.child_module,
                                "reason": "FAILED",
                                "description": [str(exc)],
                            },
                            args.child_module,
                        )
                    )
                    return "__compat_runner_ignored_2dsphere_setup_failure__"
                raise

        Collection.create_index = create_index_with_setup_tolerance

    import importlib

    module = importlib.import_module(f"tests.{args.child_module}")
    suite = unittest.defaultTestLoader.loadTestsFromModule(module)
    result_stream = open(os.devnull, "w", encoding="utf-8")
    runner = unittest.TextTestRunner(stream=result_stream, verbosity=1)
    started = time.time()
    test_result = runner.run(suite)
    elapsed = time.time() - started
    result_stream.close()

    records: List[Dict[str, Any]] = list(setup_records)
    seen_names = set()
    for _, obj in inspect.getmembers(module, inspect.isclass):
        if issubclass(obj, unittest.TestCase):
            for record in getattr(obj, "test_results", []) or []:
                normalized = normalize_record(record, args.child_module)
                records.append(normalized)
                seen_names.add(str(normalized.get("test_name")))

    for test, details in test_result.failures:
        test_name = str(test)
        if test_name not in seen_names:
            records.append(
                normalize_record(
                    {
                        "status": "fail",
                        "test_name": test_name,
                        "suite": args.child_module,
                        "reason": "ASSERTION_FAILURE",
                        "description": [details[-5000:]],
                    },
                    args.child_module,
                )
            )
    for test, details in test_result.errors:
        test_name = str(test)
        if test_name not in seen_names:
            records.append(
                normalize_record(
                    {
                        "status": "fail",
                        "test_name": test_name,
                        "suite": args.child_module,
                        "reason": "HARNESS_ERROR",
                        "description": [details[-5000:]],
                    },
                    args.child_module,
                )
            )

    payload = {
        "module": args.child_module,
        "elapsed": elapsed,
        "records": records,
        "unittest": {
            "testsRun": test_result.testsRun,
            "failures": len(test_result.failures),
            "errors": len(test_result.errors),
            "skipped": len(test_result.skipped),
            "successful": test_result.wasSuccessful(),
        },
    }
    Path(args.child_output).write_text(json.dumps(payload, indent=2, default=str), encoding="utf-8")
    return 0


def summarize(records: Iterable[Dict[str, Any]]) -> Dict[str, Any]:
    rows = list(records)
    total = len(rows)
    passed = sum(1 for row in rows if status_of(row) == "pass")
    failed = total - passed
    by_category: Dict[str, Counter] = defaultdict(Counter)
    by_module: Dict[str, Counter] = defaultdict(Counter)
    reasons = Counter()
    for row in rows:
        state = "pass" if status_of(row) == "pass" else "fail"
        by_category[row.get("category", "Unknown")][state] += 1
        by_module[row.get("module", "Unknown")][state] += 1
        if state == "fail":
            reasons[str(row.get("reason", "FAILED"))] += 1
    return {
        "total": total,
        "passed": passed,
        "failed": failed,
        "pass_rate": round((passed / total * 100), 2) if total else 0,
        "by_category": by_category,
        "by_module": by_module,
        "failure_reasons": reasons,
    }


def markdown_table(headers: List[str], rows: Iterable[Iterable[Any]]) -> str:
    lines = ["| " + " | ".join(headers) + " |", "| " + " | ".join(["---"] * len(headers)) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(str(item) for item in row) + " |")
    return "\n".join(lines)


def first_error(record: Dict[str, Any]) -> str:
    desc = record.get("description", "")
    if isinstance(desc, list):
        desc = "; ".join(str(item) for item in desc[:2])
    text = str(desc).replace("\n", " ")
    return text[:220]


def write_report(output_dir: Path, uri: str, db_name: str, modules: List[str], skipped: Dict[str, str], records: List[Dict[str, Any]], module_runs: List[Dict[str, Any]]) -> Path:
    summary = summarize(records)
    report_path = output_dir / "compatibility_report.md"
    generated = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

    category_rows = []
    for category, counter in sorted(summary["by_category"].items()):
        total = counter["pass"] + counter["fail"]
        category_rows.append([category, total, counter["pass"], counter["fail"], f"{(counter['pass'] / total * 100):.2f}%" if total else "0.00%"])

    module_rows = []
    for module, counter in sorted(summary["by_module"].items()):
        total = counter["pass"] + counter["fail"]
        module_rows.append([module, CATEGORY_BY_MODULE.get(module, module), total, counter["pass"], counter["fail"], f"{(counter['pass'] / total * 100):.2f}%" if total else "0.00%"])

    top_failures = [row for row in records if status_of(row) != "pass"][:40]
    failure_rows = [[row.get("category"), row.get("test_name"), row.get("reason"), first_error(row)] for row in top_failures]

    run_rows = []
    for run in module_runs:
        run_rows.append([run["module"], run["status"], f"{run.get('elapsed', 0):.2f}s", run.get("records", 0), run.get("note", "")])

    skipped_rows = [[name, reason] for name, reason in skipped.items()]

    content = [
        "# MongoDB Compatibility Report",
        "",
        f"- Generated: {generated}",
        f"- Target: `{redact_uri(uri)}`",
        f"- Test database: `{db_name}`",
        f"- Test source: `mongodb-developer/service-tests` Python compatibility suite",
        f"- Compatibility score: **{summary['passed']}/{summary['total']} passed ({summary['pass_rate']:.2f}%)**",
        "",
        "## Category Summary",
        "",
        markdown_table(["Category", "Total", "Passed", "Failed", "Pass Rate"], category_rows),
        "",
        "## Module Summary",
        "",
        markdown_table(["Module", "Category", "Total", "Passed", "Failed", "Pass Rate"], module_rows),
        "",
        "## Module Execution",
        "",
        markdown_table(["Module", "Status", "Elapsed", "Records", "Note"], run_rows),
        "",
        "## Failure Reasons",
        "",
        markdown_table(["Reason", "Count"], summary["failure_reasons"].most_common()),
        "",
        "## First Failing Checks",
        "",
        markdown_table(["Category", "Check", "Reason", "First Error"], failure_rows) if failure_rows else "No failing checks recorded.",
        "",
        "## Skipped By Default",
        "",
        markdown_table(["Module", "Reason"], skipped_rows),
        "",
        "## Scope Notes",
        "",
        "- This report uses the repository's Python compatibility suite, not the heavier Docker/resmoke runner.",
        "- The score counts individual result documents emitted by the suite. Some unittest methods emit multiple command-level checks.",
        "- Skipped modules are excluded from the score because they can mutate cluster/user/replica state or depend on external binaries/runtime.",
    ]
    report_path.write_text("\n".join(content) + "\n", encoding="utf-8")
    return report_path


def parent_main(args: argparse.Namespace) -> int:
    if not args.uri:
        raise SystemExit("Provide --uri or MONGODB_COMPAT_URI")
    db_name = args.db_name or f"compat_service_tests_{now_stamp()}"
    output_dir = Path(args.output_dir or f"reports/mongodb_compat_{now_stamp()}").resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    logs_dir = output_dir / "module_logs"
    logs_dir.mkdir(parents=True, exist_ok=True)

    modules = module_list(args)
    skipped = dict(SKIPPED_BY_DEFAULT)
    if args.modules.strip():
        skipped = {}

    all_records: List[Dict[str, Any]] = []
    module_runs: List[Dict[str, Any]] = []
    child_outputs_dir = output_dir / "module_json"
    child_outputs_dir.mkdir(exist_ok=True)

    for module in modules:
        child_output = child_outputs_dir / f"{module}.json"
        stdout_path = logs_dir / f"{module}.stdout.log"
        stderr_path = logs_dir / f"{module}.stderr.log"
        command = [
            sys.executable,
            str(Path(__file__).resolve()),
            "--child-module",
            module,
            "--child-output",
            str(child_output),
            "--uri",
            args.uri,
            "--db-name",
            db_name,
            "--service-tests-dir",
            args.service_tests_dir,
        ]
        started = time.time()
        with stdout_path.open("w", encoding="utf-8") as stdout, stderr_path.open("w", encoding="utf-8") as stderr:
            try:
                completed = subprocess.run(command, stdout=stdout, stderr=stderr, timeout=args.timeout, check=False)
                elapsed = time.time() - started
            except subprocess.TimeoutExpired:
                elapsed = time.time() - started
                module_runs.append({"module": module, "status": "timeout", "elapsed": elapsed, "records": 0, "note": f">{args.timeout}s"})
                all_records.append(
                    normalize_record(
                        {
                            "status": "fail",
                            "test_name": f"{module} timed out",
                            "suite": module,
                            "reason": "TIMEOUT",
                            "description": [f"Module exceeded {args.timeout}s timeout"],
                        },
                        module,
                    )
                )
                continue

        if completed.returncode != 0:
            note = stderr_path.read_text(encoding="utf-8", errors="replace")[-500:]
            module_runs.append({"module": module, "status": f"error({completed.returncode})", "elapsed": elapsed, "records": 0, "note": note.replace("\n", " ")[:220]})
            all_records.append(
                normalize_record(
                    {
                        "status": "fail",
                        "test_name": f"{module} runner error",
                        "suite": module,
                        "reason": "RUNNER_ERROR",
                        "description": [note],
                    },
                    module,
                )
            )
            continue

        try:
            payload = json.loads(child_output.read_text(encoding="utf-8"))
            records = payload.get("records", [])
        except Exception:
            records = []
            payload = {"elapsed": elapsed}
            module_runs.append({"module": module, "status": "invalid-output", "elapsed": elapsed, "records": 0, "note": traceback.format_exc()[-220:]})
        else:
            all_records.extend(records)
            status = "ok" if payload.get("unittest", {}).get("successful") else "completed-with-failures"
            module_runs.append({"module": module, "status": status, "elapsed": payload.get("elapsed", elapsed), "records": len(records), "note": ""})

    summary_path = output_dir / "compatibility_results.json"
    summary_payload = {
        "target": redact_uri(args.uri),
        "db_name": db_name,
        "modules": modules,
        "skipped": skipped,
        "summary": summarize(all_records),
        "module_runs": module_runs,
        "records": all_records,
    }
    summary_path.write_text(json.dumps(summary_payload, indent=2, default=str), encoding="utf-8")
    report_path = write_report(output_dir, args.uri, db_name, modules, skipped, all_records, module_runs)

    print(json.dumps({"output_dir": str(output_dir), "json": str(summary_path), "report": str(report_path), "summary": summary_payload["summary"]}, indent=2, default=str))
    return 0


def main() -> int:
    args = parse_args()
    if args.child_module:
        return child_main(args)
    return parent_main(args)


if __name__ == "__main__":
    raise SystemExit(main())
