import { expect, test } from "@playwright/test";

async function backendAvailable() {
  try {
    const response = await fetch("http://localhost:8080/api/health");
    return response.ok;
  } catch {
    return false;
  }
}

test("dashboard login screen loads", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByText("Hanul AI-PACS Assistant").first()).toBeVisible();
});

test("public app routes load the shell", async ({ page }) => {
  await page.goto("/studies");
  await expect(page.getByText("Hanul AI-PACS Assistant").first()).toBeVisible();
  await page.goto("/architecture");
  await expect(page.getByText("Hanul AI-PACS Assistant").first()).toBeVisible();
});

test("login and core pages load when backend is available", async ({ page }) => {
  test.skip(!(await backendAvailable()), "Backend is not running; skipping authenticated smoke flow.");
  await page.goto("/");
  await page.getByLabel("사용자 이름").fill("demo");
  await page.getByLabel("비밀번호").fill("demo");
  await page.getByRole("button", { name: "로그인" }).click();
  await expect(page.getByText("심사용 데모 모드")).toBeVisible();

  await page.getByRole("link", { name: /검사 목록/ }).click();
  await expect(page.getByText("검사 목록").first()).toBeVisible();

  await page.getByRole("link", { name: /아키텍처/ }).click();
  await expect(page.getByText("아키텍처").first()).toBeVisible();
});
