#!/usr/bin/env python3
"""
HuskyApply Brain Service Validation Script

This script performs comprehensive validation of the Brain service
to ensure all components are properly configured and functioning.
"""

import asyncio
import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


# Color codes for terminal output
class Colors:
    RED = "\033[0;31m"
    GREEN = "\033[0;32m"
    YELLOW = "\033[1;33m"
    BLUE = "\033[0;34m"
    PURPLE = "\033[0;35m"
    CYAN = "\033[0;36m"
    WHITE = "\033[1;37m"
    NC = "\033[0m"  # No Color


@dataclass
class ValidationResult:
    """Result of a validation check."""

    name: str
    status: str  # 'pass', 'warning', 'fail'
    message: str
    details: Optional[Dict[str, Any]] = None


class BrainValidator:
    """Comprehensive validator for the Brain service."""

    def __init__(self) -> None:
        self.results: List[ValidationResult] = []
        self.project_root = Path(__file__).parent

    def log(self, message: str, color: str = Colors.WHITE) -> None:
        """Log a message with color."""
        print(f"{color}{message}{Colors.NC}")

    def add_result(
        self, name: str, status: str, message: str, details: Optional[Dict[str, Any]] = None
    ) -> None:
        """Add a validation result."""
        result = ValidationResult(name, status, message, details)
        self.results.append(result)

        # Print result immediately
        if status == "pass":
            self.log(f"✅ {name}: {message}", Colors.GREEN)
        elif status == "warning":
            self.log(f"⚠️  {name}: {message}", Colors.YELLOW)
        else:
            self.log(f"❌ {name}: {message}", Colors.RED)

    def validate_file_structure(self) -> None:
        """Validate that all required files exist."""
        self.log("\n🔍 Validating file structure...", Colors.BLUE)

        required_files = [
            "main.py",
            "ai_chain.py",
            "retry_utils.py",
            "tracing_utils.py",
            "exceptions.py",
            "monitoring.py",
            "pyproject.toml",
            "Dockerfile",
            ".env.example",
            "README.md",
            "Makefile",
        ]

        missing_files = []
        for file in required_files:
            file_path = self.project_root / file
            if not file_path.exists():
                missing_files.append(file)

        if missing_files:
            self.add_result("File Structure", "fail", f"Missing files: {', '.join(missing_files)}")
        else:
            self.add_result("File Structure", "pass", "All required files present")

        # Check test directory
        test_dir = self.project_root / "tests"
        if not test_dir.exists():
            self.add_result("Test Directory", "fail", "Tests directory not found")
        else:
            test_files = list(test_dir.glob("test_*.py"))
            if len(test_files) < 3:
                self.add_result(
                    "Test Files",
                    "warning",
                    f"Only {len(test_files)} test files found, consider adding more",
                )
            else:
                self.add_result("Test Files", "pass", f"Found {len(test_files)} test files")

    def validate_python_syntax(self) -> None:
        """Validate Python syntax for all Python files."""
        self.log("\n🐍 Validating Python syntax...", Colors.BLUE)

        python_files = list(self.project_root.glob("*.py"))
        python_files.extend(self.project_root.glob("tests/*.py"))
        python_files.extend(self.project_root.glob("config/*.py"))

        syntax_errors = []
        for file_path in python_files:
            try:
                with open(file_path, "r") as f:
                    compile(f.read(), file_path, "exec")
            except SyntaxError as e:
                syntax_errors.append(f"{file_path}: {e}")

        if syntax_errors:
            self.add_result(
                "Python Syntax",
                "fail",
                f"Syntax errors found in {len(syntax_errors)} files",
                {"errors": syntax_errors},
            )
        else:
            self.add_result(
                "Python Syntax", "pass", f"All {len(python_files)} Python files have valid syntax"
            )

    def validate_imports(self) -> None:
        """Validate that all imports can be resolved."""
        self.log("\n📦 Validating imports...", Colors.BLUE)

        try:
            # Test core imports
            sys.path.insert(0, str(self.project_root))

            modules = [
                "main",
                "ai_chain",
                "retry_utils",
                "tracing_utils",
                "exceptions",
                "monitoring",
            ]

            import_errors = []
            for module in modules:
                try:
                    __import__(module)
                except ImportError as e:
                    import_errors.append(f"{module}: {e}")

            if import_errors:
                self.add_result(
                    "Import Validation",
                    "fail",
                    f"Import errors in {len(import_errors)} modules",
                    {"errors": import_errors},
                )
            else:
                self.add_result(
                    "Import Validation", "pass", f"All {len(modules)} core modules can be imported"
                )

        except Exception as e:
            self.add_result("Import Validation", "fail", f"Import validation failed: {e}")

    def validate_environment_config(self) -> None:
        """Validate environment configuration."""
        self.log("\n⚙️ Validating environment configuration...", Colors.BLUE)

        # Check .env.example exists and has required variables
        env_example = self.project_root / ".env.example"
        if not env_example.exists():
            self.add_result("Environment Template", "fail", ".env.example file not found")
            return

        with open(env_example, "r") as f:
            env_content = f.read()

        required_vars = [
            "OPENAI_API_KEY",
            "INTERNAL_API_KEY",
            "RABBITMQ_HOST",
            "RABBITMQ_USER",
            "RABBITMQ_PASSWORD",
            "GATEWAY_INTERNAL_URL",
        ]

        missing_vars = []
        for var in required_vars:
            if var not in env_content:
                missing_vars.append(var)

        if missing_vars:
            self.add_result(
                "Environment Variables",
                "warning",
                f"Missing environment variables in .env.example: {', '.join(missing_vars)}",
            )
        else:
            self.add_result(
                "Environment Variables", "pass", "All required environment variables documented"
            )

    def validate_docker_config(self) -> None:
        """Validate Docker configuration."""
        self.log("\n🐳 Validating Docker configuration...", Colors.BLUE)

        dockerfile = self.project_root / "Dockerfile"
        if not dockerfile.exists():
            self.add_result("Dockerfile", "fail", "Dockerfile not found")
            return

        with open(dockerfile, "r") as f:
            dockerfile_content = f.read()

        # Check for security best practices
        checks = {
            "Non-root user": "USER " in dockerfile_content,
            "Health check": "HEALTHCHECK" in dockerfile_content,
            "Multi-stage build": "FROM " in dockerfile_content and " as " in dockerfile_content,
            "Proper COPY ownership": "--chown=" in dockerfile_content,
        }

        failed_checks = [check for check, passed in checks.items() if not passed]

        if failed_checks:
            self.add_result(
                "Docker Security",
                "warning",
                f"Security recommendations not followed: {', '.join(failed_checks)}",
            )
        else:
            self.add_result("Docker Security", "pass", "Docker security best practices followed")

        # Check .dockerignore
        dockerignore = self.project_root / ".dockerignore"
        if dockerignore.exists():
            self.add_result("Docker Ignore", "pass", ".dockerignore file present")
        else:
            self.add_result(
                "Docker Ignore",
                "warning",
                ".dockerignore file not found - build context may be large",
            )

    def validate_type_hints(self) -> None:
        """Validate type hints using mypy."""
        self.log("\n🔍 Validating type hints...", Colors.BLUE)

        try:
            result = subprocess.run(  # nosec B603 B607 - controlled command for type checking
                [
                    "python",
                    "-m",
                    "mypy",
                    ".",
                    "--ignore-missing-imports",
                    "--explicit-package-bases",
                ],
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=60,
            )

            if result.returncode == 0:
                self.add_result("Type Hints", "pass", "All type hints are valid")
            else:
                error_count = result.stdout.count("error:")
                self.add_result(
                    "Type Hints",
                    "warning" if error_count < 5 else "fail",
                    f"MyPy found {error_count} type errors",
                    {"output": result.stdout},
                )

        except subprocess.TimeoutExpired:
            self.add_result("Type Hints", "warning", "Type checking timed out")
        except FileNotFoundError:
            self.add_result(
                "Type Hints", "warning", "MyPy not available - install with 'pip install mypy'"
            )
        except Exception as e:
            self.add_result("Type Hints", "warning", f"Type checking failed: {e}")

    def validate_code_quality(self) -> None:
        """Validate code formatting and style."""
        self.log("\n✨ Validating code quality...", Colors.BLUE)

        # Check if black would make changes
        try:
            result = (
                subprocess.run(  # nosec B603 B607 - controlled command for code formatting check
                    ["python", "-m", "black", "--check", "."],
                    cwd=self.project_root,
                    capture_output=True,
                    text=True,
                    timeout=30,
                )
            )

            if result.returncode == 0:
                self.add_result("Code Formatting", "pass", "Code is properly formatted with Black")
            else:
                self.add_result("Code Formatting", "warning", "Code needs formatting with Black")

        except (subprocess.TimeoutExpired, FileNotFoundError):
            self.add_result(
                "Code Formatting",
                "warning",
                "Black not available - install with 'pip install black'",
            )

        # Check import sorting
        try:
            result = (
                subprocess.run(  # nosec B603 B607 - controlled command for import sorting check
                    ["python", "-m", "isort", "--check-only", "."],
                    cwd=self.project_root,
                    capture_output=True,
                    text=True,
                    timeout=30,
                )
            )

            if result.returncode == 0:
                self.add_result("Import Sorting", "pass", "Imports are properly sorted")
            else:
                self.add_result("Import Sorting", "warning", "Imports need sorting with isort")

        except (subprocess.TimeoutExpired, FileNotFoundError):
            self.add_result("Import Sorting", "warning", "isort not available")

    def validate_tests(self) -> None:
        """Validate test suite."""
        self.log("\n🧪 Validating tests...", Colors.BLUE)

        try:
            result = subprocess.run(  # nosec B603 B607 - controlled command for test collection
                ["python", "-m", "pytest", "--collect-only", "-q"],
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=30,
            )

            if result.returncode == 0:
                # Count collected tests
                lines = result.stdout.strip().split("\n")
                test_count = 0
                for line in lines:
                    if " collected" in line:
                        test_count = int(line.split()[0])
                        break

                if test_count > 0:
                    self.add_result("Test Discovery", "pass", f"Found {test_count} tests")
                else:
                    self.add_result("Test Discovery", "warning", "No tests found")
            else:
                self.add_result(
                    "Test Discovery", "fail", "Test collection failed", {"output": result.stderr}
                )

        except subprocess.TimeoutExpired:
            self.add_result("Test Discovery", "warning", "Test discovery timed out")
        except FileNotFoundError:
            self.add_result("Test Discovery", "warning", "pytest not available")

    def validate_documentation(self) -> None:
        """Validate documentation quality."""
        self.log("\n📚 Validating documentation...", Colors.BLUE)

        readme = self.project_root / "README.md"
        if not readme.exists():
            self.add_result("README", "fail", "README.md not found")
            return

        with open(readme, "r") as f:
            readme_content = f.read()

        required_sections = [
            "# ",  # Title
            "## Features",
            "## Installation",
            "## Configuration",
            "## Usage",
            "## API",
            "## Testing",
            "## Deployment",
        ]

        missing_sections = []
        for section in required_sections:
            if section not in readme_content:
                missing_sections.append(section.strip("# "))

        if missing_sections:
            self.add_result(
                "Documentation Completeness",
                "warning",
                f"README missing sections: {', '.join(missing_sections)}",
            )
        else:
            self.add_result(
                "Documentation Completeness", "pass", "README has all required sections"
            )

        # Check documentation length (should be comprehensive)
        if len(readme_content) < 5000:
            self.add_result(
                "Documentation Detail",
                "warning",
                f"README is quite short ({len(readme_content)} chars) - consider adding more detail",
            )
        else:
            self.add_result(
                "Documentation Detail",
                "pass",
                f"README is comprehensive ({len(readme_content)} chars)",
            )

    def validate_security(self) -> None:
        """Validate security configuration."""
        self.log("\n🔒 Validating security configuration...", Colors.BLUE)

        # Check for hardcoded secrets
        security_issues = []

        python_files = list(self.project_root.glob("*.py"))
        python_files.extend(self.project_root.glob("tests/*.py"))

        secret_patterns = ["password", "secret", "key", "token", "api_key"]

        for file_path in python_files:
            try:
                with open(file_path, "r") as f:
                    content = f.read().lower()
                    for pattern in secret_patterns:
                        if f"{pattern}=" in content or f"{pattern} =" in content:
                            if "os.getenv" not in content.split(f"{pattern}")[1].split("\n")[0]:
                                security_issues.append(
                                    f"Potential hardcoded {pattern} in {file_path}"
                                )
            except (
                OSError,
                ValueError,
                UnicodeDecodeError,
            ):  # nosec B112 - specific exceptions for file ops
                # Skip files that can't be read or decoded
                continue

        if security_issues:
            self.add_result(
                "Security Scan",
                "warning",
                f"Potential security issues found: {len(security_issues)}",
                {"issues": security_issues},
            )
        else:
            self.add_result("Security Scan", "pass", "No obvious security issues found")

    async def run_validation(self) -> Tuple[int, int, int]:
        """Run all validation checks."""
        self.log("🧠 HuskyApply Brain Service Validation", Colors.CYAN)
        self.log("=" * 50, Colors.CYAN)

        # Run all validations
        validations = [
            self.validate_file_structure,
            self.validate_python_syntax,
            self.validate_imports,
            self.validate_environment_config,
            self.validate_docker_config,
            self.validate_type_hints,
            self.validate_code_quality,
            self.validate_tests,
            self.validate_documentation,
            self.validate_security,
        ]

        for validation in validations:
            try:
                validation()
            except Exception as e:
                self.add_result(validation.__name__, "fail", f"Validation failed: {e}")

        # Count results
        passed = len([r for r in self.results if r.status == "pass"])
        warnings = len([r for r in self.results if r.status == "warning"])
        failed = len([r for r in self.results if r.status == "fail"])

        return passed, warnings, failed

    def print_summary(self, passed: int, warnings: int, failed: int) -> None:
        """Print validation summary."""
        self.log("\n" + "=" * 50, Colors.CYAN)
        self.log("📊 VALIDATION SUMMARY", Colors.CYAN)
        self.log("=" * 50, Colors.CYAN)

        total = passed + warnings + failed
        self.log(f"Total checks: {total}", Colors.WHITE)
        self.log(f"✅ Passed: {passed}", Colors.GREEN)
        self.log(f"⚠️  Warnings: {warnings}", Colors.YELLOW)
        self.log(f"❌ Failed: {failed}", Colors.RED)

        if failed == 0 and warnings <= 2:
            self.log("\n🎉 BRAIN SERVICE IS PERFECT! 🎉", Colors.GREEN)
            self.log("All critical validations passed!", Colors.GREEN)
        elif failed == 0:
            self.log("\n✅ BRAIN SERVICE IS GOOD!", Colors.YELLOW)
            self.log("No critical issues, but consider addressing warnings.", Colors.YELLOW)
        else:
            self.log("\n❌ BRAIN SERVICE NEEDS WORK", Colors.RED)
            self.log("Please address the failed checks before deployment.", Colors.RED)

        # Print detailed results for failures
        if failed > 0:
            self.log("\n🔍 FAILED CHECKS:", Colors.RED)
            for result in self.results:
                if result.status == "fail":
                    self.log(f"  ❌ {result.name}: {result.message}", Colors.RED)

        # Print warnings
        if warnings > 0:
            self.log("\n⚠️  WARNINGS:", Colors.YELLOW)
            for result in self.results:
                if result.status == "warning":
                    self.log(f"  ⚠️  {result.name}: {result.message}", Colors.YELLOW)


async def main() -> None:
    """Main validation entry point."""
    validator = BrainValidator()

    try:
        passed, warnings, failed = await validator.run_validation()
        validator.print_summary(passed, warnings, failed)

        # Exit with appropriate code
        if failed > 0:
            sys.exit(1)
        elif warnings > 5:
            sys.exit(2)
        else:
            sys.exit(0)

    except KeyboardInterrupt:
        validator.log("\n❌ Validation interrupted by user", Colors.RED)
        sys.exit(1)
    except Exception as e:
        validator.log(f"\n❌ Validation failed with error: {e}", Colors.RED)
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
