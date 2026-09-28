"""Posts GitHub repo events (stars, forks, issues, comments, PRs, workflow runs) to the Telegram logs topic, styled like ArchiveTune's notifier bot."""

import json
import os
import re
import sys

import requests

MD_V2_SPECIAL = r"_*[]()~`>#+-=|{}.!"


def esc(text):
    return re.sub(r"([%s])" % re.escape(MD_V2_SPECIAL), r"\\\1", str(text))


def esc_url(url):
    return str(url).replace("\\", "\\\\").replace(")", "\\)")


def link(text, url):
    return f"[{esc(text)}]({esc_url(url)})"


def main():
    event = os.environ.get("GITHUB_EVENT_NAME", "")
    with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as f:
        payload = json.load(f)

    repo = payload["repository"]
    repo_full = repo["full_name"]
    repo_name = repo["name"]
    repo_url = repo["html_url"]
    repo_link = link(repo_full, repo_url)

    def user_link(login):
        return link(login, f"https://github.com/{login}")

    message = None

    if event == "watch":
        sender = payload["sender"]["login"]
        message = (
            f"{repo_link} • Star\n"
            f"{user_link(sender)} starred {link(repo_name, repo_url)}"
        )
    elif event == "fork":
        sender = payload["sender"]["login"]
        forkee = payload["forkee"]
        message = (
            f"{repo_link} • Fork\n"
            f"{user_link(sender)} forked {link(forkee['full_name'], forkee['html_url'])}\n"
            f"From: {link(repo_name, repo_url)}"
        )
    elif event == "issues":
        issue = payload["issue"]
        verb = {
            "opened": "Opened",
            "closed": "Closed",
            "reopened": "Reopened",
            "labeled": "Labeled",
            "unlabeled": "Unlabeled",
        }.get(payload.get("action", ""), payload.get("action", "").capitalize())
        sender = payload["sender"]["login"]
        labels = ", ".join(label["name"] for label in issue.get("labels", [])) or "none"
        issue_ref = link(f"{repo_name}#{issue['number']}", issue["html_url"])
        message = (
            f"{repo_link} • Issue\n"
            f"{user_link(sender)} {esc(verb)} {issue_ref} {esc(issue['title'])}\n"
            f"State: {esc(issue['state'].upper())} • Author: {user_link(issue['user']['login'])}\n"
            f"Labels: {esc(labels)}"
        )
    elif event == "issue_comment":
        comment = payload["comment"]
        if comment["user"]["type"] == "Bot":
            print("Skipping bot-authored comment.")
            return
        issue = payload["issue"]
        kind = "PR" if "pull_request" in issue else "Issue"
        body = comment.get("body") or ""
        if len(body) > 800:
            body = body[:800] + "…"
        issue_ref = link(f"{repo_name}#{issue['number']}", comment["html_url"])
        message = (
            f"{repo_link} • Comment\n"
            f"{user_link(comment['user']['login'])} Commented {issue_ref} {esc(issue['title'])}\n"
            f"{kind}: {esc(issue['state'].upper())}\n\n{esc(body)}"
        )
    elif event == "pull_request":
        pr = payload["pull_request"]
        verb = {
            "opened": "Opened",
            "closed": "Closed",
            "reopened": "Reopened",
            "ready_for_review": "Marked ready",
        }.get(payload.get("action", ""), payload.get("action", "").capitalize())
        sender = payload["sender"]["login"]
        state = "MERGED" if pr.get("merged") else pr["state"].upper()
        pr_ref = link(f"{repo_name}#{pr['number']}", pr["html_url"])
        message = (
            f"{repo_link} • Pull Request\n"
            f"{user_link(sender)} {esc(verb)} {pr_ref} {esc(pr['title'])}\n"
            f"State: {esc(state)} • Author: {user_link(pr['user']['login'])}"
        )
    elif event == "workflow_run":
        run = payload["workflow_run"]
        if run["name"] == "Notify GitHub events":
            print("Skipping self-triggered run.")
            return
        conclusion = (run.get("conclusion") or "unknown").upper()
        message = f"{repo_link} • Actions\n{esc(run['name'])} {esc(conclusion)}"

    if message is None:
        print(f"No message built for event '{event}'; nothing to send.")
        return

    bot_token = os.environ["BOT_TOKEN"]
    chat_id = os.environ["CHAT_ID"]
    topic_id = os.environ.get("TOPIC_ID") or os.environ.get("LOGS_TOPIC_ID")

    telegram_payload = {
        "chat_id": chat_id,
        "text": message,
        "parse_mode": "MarkdownV2",
        "disable_web_page_preview": True,
    }
    if topic_id:
        telegram_payload["message_thread_id"] = topic_id

    response = requests.post(
        f"https://api.telegram.org/bot{bot_token}/sendMessage",
        json=telegram_payload,
        timeout=30,
    )
    if response.status_code != 200:
        print(f"Failed to send message: {response.status_code} {response.text}")
        sys.exit(1)
    print("Message sent successfully.")


if __name__ == "__main__":
    main()
