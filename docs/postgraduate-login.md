# Postgraduate login

This branch starts from the locally tested 3.3.0 baseline (044ef836).

Sign-in now authenticates Wisdom AHU (ADWMH), tries undergraduate JWXT, and
only after JWXT fails tries GMIS. A successful GMIS student session marks the
account as POSTGRADUATE. A failed request alone never determines the account type.
Existing persisted accounts retain undergraduate behavior until their next login.

The public GMIS entry is https://gmis.ahu.edu.cn/gmis5/oauthLogin/ahdx.
On 2026-09-25 the entry redirected to central CAS with
service=http://gmis.ahu.edu.cn/gmis5/oauthLogin/ahdx. The client preserves this
service identifier but transports GMIS redirects and tickets over HTTPS.
CAS forms use the returned lt, execution and form action, not a fixed execution value.

The live student shell is /gmis5/(S(...))/student/default/index, titled 学生端,
with a protected /student/default/home frame in the same URL session.
Both routes were checked anonymously and redirect to /home/stulogin.
The client verifies the shell and then fetches its protected frame; a shell alone
does not prove authentication. A direct student content page alternatively needs
account identity, a logout control and student navigation. Login/error/password/
role-selection/teacher pages and non-student routes are rejected.
Live validation on MEIZU 21 confirmed JWXT rejection followed by GMIS verified=true
and POSTGRADUATE identity. Settings displayed 研究生账号 and home hid undergraduate
navigation while keeping shared services. Account-type updates are observable so
silent session refresh also updates navigation without a restart.
Diagnostic logs use the AcademicLogin tag and omit identifiers, tickets and HTML.

Postgraduate accounts hide undergraduate timetable data, grades/GPA, exams, evaluation,
free classrooms, undergraduate semester settings and course reminder controls.
Home widget libraries and both bottom-navigation variants use the same account policy.
Direct routes and academic requests are also gated. Desktop timetable widgets now
read GMIS data through a separate widget snapshot (see below). Campus-card/Wisdom services, payments, lost-and-found,
school calendar, resources, weather and separately authenticated Chaoxing remain.

The login stage previously passed 260 unit tests and built a Debug APK.
After a cold restart, Settings still showed 研究生账号 and no undergraduate login
was attempted. The complete tools page exposed shared services and zero undergraduate
entries. No app data was cleared for validation.
Manual checks: undergraduate sign-in preserves academic features; a real postgraduate
sign-in shows “研究生账号” in Settings and hides undergraduate features; restart and
switch accounts to confirm persistence and restore undergraduate features.

## GMIS timetable

The existing Schedule page, seven-column weekly grid, CourseCard, overview mode and
course detail dialog are shared with undergraduate accounts. GMIS responses are
adapted into the existing Course model; there is no separate graduate timetable page.
The graduate term selector is in the existing timetable settings dialog.

The client derives a fresh session prefix from the GMIS student index and reads
student/pygl/xskbcx, student/default/bindterm and student/pygl/py_kbcx_ew.
The last endpoint is a read-only form POST with kblx=xs and the selected termcode.
Term codes and selection come from bindterm, never a hardcoded semester.
Requests include the timetable Referer and XMLHttpRequest header. The transport
whitelists these query routes and form keys; enrollment and withdrawal are not used.

AJAX bodies are decoded using the public https://gmis.ahu.edu.cn/gmis5/Scripts/rajax.js
AES/ECB/PKCS7 protocol (Java AES/ECB/PKCS5Padding is equivalent for AES blocks).
Plain JSON is also accepted. Login HTML triggers at most one session renewal;
malformed data raises an error instead of being treated as an empty timetable.

Courses repeated across adjacent periods are merged per weekday, week set and
morning/afternoon/evening group. Multiple courses in a cell remain independent.
The server supplies times including period 14. Untimed/unknown-week entries remain
available under 未排定课程 rather than receiving invented grid positions.
The root week field is a weekday name, not the teaching week. On first opening the
current graduate term, a required dialog asks the user to enter the current teaching
week (1–60). The first-week Monday is derived from that input and stored in the
existing encrypted per-account settings under a separate GMIS term key. The week
then advances on Mondays and drives the original date labels and current-week jump.
A graduate-only current-week field inside the existing top-right timetable settings
permits changes at any time. No additional toolbar button is used. Historical
terms do not force a new current-week prompt; their calendar can be configured in
the same timetable settings dialog. Undergraduate week calculation and controls
remain unchanged. Course reminders remain disabled.

GMIS room labels omit the repeated （江淮） campus tag. The observed building names
教学主楼、教学主楼北阶、教学主楼二楼阶梯 display as 主楼、主楼北阶、主楼二阶,
with room numbers unchanged. Unknown buildings keep their full names. This
normalization is applied only to GMIS course data; undergraduate locations keep
their existing formatter.

GMIS term lists and timetables use versioned encrypted storage scoped to the account
and term, following the undergraduate cache behavior. Opening the timetable or home
reads that cache without contacting GMIS when the current term is present. Explicit
refresh replaces the cached selection. Choosing an uncached term fetches that term
only. A failed refresh keeps the previous timetable visible with an error message.
Home uses the selected current term, the user-provided teaching-week anchor and
exact week memberships to show today's courses. Courses without a verified time
or section remain outside the timed home cards.

WebVPN was used only for read-only investigation. Its locally supplied cookies
were kept in memory and sent only to wvpn.ahu.edu.cn, never to the native app or
another host. Tests use fictional courses and an independently generated AES vector.

## Desktop timetable widgets

Both Glance and adaptive RemoteViews widgets use the undergraduate rendering and
refresh triggers: show cache first, request a unique WorkManager refresh, then redraw
after completion. The operating system controls delivery of widget updates; returning
to the launcher does not provide a guaranteed visibility callback. Existing periodic,
manual, add-widget and resize triggers are retained.

Graduate widgets reuse the GMIS timetable adapter, explicit week memberships, verified
clock ranges (including period 14), and the term's user-confirmed first-week Monday.
Missing calendars and unknown-time courses show instructions instead of invented
undergraduate times or a misleading empty timetable. Foreground cache/week revisions
request a cache-only widget redraw.

Widget responses are saved atomically under `gmis.widget.schedule.v1` in the encrypted
account box. They do not write the timetable page's cache, revision, semester settings
or reminder read model. Existing page caches can seed the widget. A foreground cache
change after a widget request starts takes priority over that request's saved result.
The widget's subtle acquisition label is the last successful network fetch time;
old page caches have no fetch timestamp and are displayed with an unknown timestamp.
Failed requests retain both courses and the previous acquisition time.

The widget copies valid CAS/GMIS cookies into a private in-memory jar and uses only
session-check GETs and the read-only timetable query POST. Response cookies are never
persisted to the app's cookie jar. Widgets do not submit passwords or renew expired
login credentials; an expired session asks the user to open the app and then refresh.
Account identity is checked around network work, and writes use the captured account
box. Logout/relogin with the same account also cancels an old request.

Automated coverage includes cache-only rendering, missing calendar, exact weeks,
GMIS period 14, account changes, same-account relogin, failed/expired refreshes,
foreground cache races, cache replacement, and cookie isolation. Live graduate
account verification remains a separate manual check.

Validation on 2026-10-01, based on develop `d8f4c47a`:

- `adb devices` found an authorized Android device before each build.
- Gradle `:app:testDebugUnitTest --tests '*appwidget.*' --tests '*crawler.gmis.*'
  --tests '*ModuleBoundaryTest' :background:testDebugUnitTest
  :data:schedule:testDebugUnitTest`: 94 tests passed (54 app, 18 background, 22 schedule).
- `:app:assembleDebug :app:assembleRelease`: both passed, including release R8 and vital lint.
  JDK 24 and a temporary dependency-mirror init script outside the repository were used.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: success. Force-stop followed
  by `adb shell am start -W -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity`
  reported a successful cold start. The new process had no AndroidRuntime error log.
- An initial complete app run passed 362 of 370 tests. Eight navigation/theme checks failed.
  Repeating those four test classes on the unmodified develop baseline produced the same
  eight failures out of 32 checks; the feature's related tests pass independently.
- This is a build/startup check and synthetic-data verification, not a live GMIS account
  or launcher-widget visual validation. Neither the production APK nor update service was changed.

Integration validation against develop `a0007d64` on 2026-10-01:

- Preserved the newly merged online holiday labels in both desktop widget renderers
  and retained both holiday and widget snapshot Hilt bindings.
- Repeated the above Gradle verification with `--tests '*data.calendar.*'` added:
  all 122 related tests passed (70 app, 28 background, 24 schedule), and both Debug
  and Release builds passed.
- `adb devices` found the same authorized device. The updated Debug APK installed
  successfully and cold-started successfully; its process had no AndroidRuntime error log.
- Live postgraduate account and launcher widget verification remains pending.
