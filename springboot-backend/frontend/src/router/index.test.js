import {beforeEach, describe, expect, it, vi} from "vitest";
import router from "./index.js";

describe("workspace routes", () => {
  beforeEach(() => {
    window.scrollTo = vi.fn();
  });

  it("exposes every primary learning and career destination", () => {
    const routeNames = router.getRoutes().map(route => route.name).filter(Boolean);

    expect(routeNames).toEqual(expect.arrayContaining([
      "overview",
      "library",
      "study",
      "career"
    ]));
  });

  it("redirects unknown locations to the growth overview", async () => {
    await router.push("/not-a-real-page");
    await router.isReady();

    expect(router.currentRoute.value.path).toBe("/");
  });
});
