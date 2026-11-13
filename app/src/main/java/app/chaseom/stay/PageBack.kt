package app.chaseom.stay

/**
 * Round 2 C-2 (05_qa/PLEOS_COMPLIANCE_G.md, Pleos CCP guide): "CCP Back 하드키는 현재 화면의 이전 단계로 이동하거나,
 * 더 이상 이전 화면이 없는 경우 앱 또는 시스템 레벨의 Back 동작을 수행". The parked pages are Compose state in one
 * Activity, so without this the system Back hid the whole app from Settings or About.
 *
 * Returns the page one step back, or null when there is no earlier step in the app. null leaves Back to the platform
 * (API 31+: the root launcher activity moves its task to the back, i.e. Hide; it is never finished).
 * The on-screen back icons use the same rule, so the two Backs cannot disagree.
 */
fun backTarget(page: Page, mode: AppMode): Page? = when (page) {
    Page.SETTINGS -> Page.NONE
    // About opens from Settings in FULL mode; in REDUCED mode Settings is not shown, so About goes straight back.
    Page.ABOUT -> if (mode == AppMode.FULL) Page.SETTINGS else Page.NONE
    // First run has no earlier step (it is the first screen), and the stay screen is the top.
    Page.ONBOARDING, Page.NONE -> null
}
