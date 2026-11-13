"""Choose which GitHub Actions events need the automatic CI jobs."""

import os
import subprocess


def should_run(event: str, repository: str, branch: str, sha: str) -> bool:
    if event != "push":
        return True

    if branch == "develop":
        master = subprocess.run(
            ["gh", "api", f"repos/{repository}/git/ref/heads/master", "--jq", ".object.sha"],
            check=False,
            capture_output=True,
            text=True,
        )
        if master.returncode == 0 and master.stdout.strip() == sha:
            print(f"Skipping develop push: master has the same commit ({sha}).")
            return False
        if master.returncode:
            print("::warning::Could not read the master ref. Continuing with push CI selection.")

    owner = repository.split("/", 1)[0]
    open_prs = subprocess.check_output(
        [
            "gh", "api", "--method", "GET", f"repos/{repository}/pulls",
            "-f", "state=open", "-f", f"head={owner}:{branch}", "-f", "per_page=1",
            "--jq", "length",
        ],
        text=True,
    )
    if int(open_prs) > 0:
        print(f"Skipping {branch} push: an open PR checks the merge commit.")
        return False
    return True


def main() -> None:
    run = should_run(
        os.environ["GITHUB_EVENT_NAME"],
        os.environ["GITHUB_REPOSITORY"],
        os.environ["GITHUB_REF_NAME"],
        os.environ["GITHUB_SHA"],
    )
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        print(f"run={str(run).lower()}", file=output)


if __name__ == "__main__":
    main()
