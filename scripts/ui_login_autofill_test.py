import os
import re
from pathlib import Path
from playwright.sync_api import expect, sync_playwright

BASE_URL = os.getenv("UI_LOGIN_BASE_URL", "http://127.0.0.1:5173")
USERNAME = "manager"


def load_env_value(key: str) -> str | None:
    value = os.getenv(key)
    if value:
        return value

    env_path = Path(__file__).resolve().parents[1] / ".env"
    if not env_path.exists():
        return None

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        current_key, current_value = line.split("=", 1)
        if current_key.strip() == key:
            return current_value.strip()
    return None


PASSWORD = load_env_value("UI_LOGIN_PASSWORD") or "MgrD5v8cN4"


def main() -> None:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        console_messages = []
        page_errors = []
        page.on("console", lambda msg: console_messages.append(f"{msg.type}: {msg.text}"))
        page.on("pageerror", lambda err: page_errors.append(str(err)))
        page.goto(BASE_URL, wait_until="domcontentloaded")
        page.wait_for_load_state("networkidle")

        page.get_by_role("button", name=USERNAME, exact=True).click()
        expect(page.get_by_label(re.compile(r"логин", re.IGNORECASE))).to_have_value(USERNAME)
        expect(page.get_by_label(re.compile(r"пароль", re.IGNORECASE))).to_have_value(PASSWORD)

        page.locator("button[type='submit']").click()
        page.wait_for_load_state("networkidle")

        error_alert = page.locator(".login-error")
        if error_alert.count() > 0:
            page.screenshot(path="output/ui_login_autofill_failed.png", full_page=True)
            raise AssertionError(f"Login failed: {error_alert.first.text_content()}")

        logout_button = page.get_by_role("button", name=re.compile(r"выйти", re.IGNORECASE))
        try:
            expect(logout_button).to_be_visible(timeout=20_000)
        except Exception:
            page.screenshot(path="output/ui_login_autofill_missing_logout.png", full_page=True)
            diagnostics = "\n".join(console_messages + page_errors)
            raise AssertionError(
                f"Login did not reach authenticated shell. URL: {page.url}\n{diagnostics}"
            )

        browser.close()


if __name__ == "__main__":
    main()
