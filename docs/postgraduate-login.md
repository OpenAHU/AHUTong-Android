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
free classrooms, semester settings, home course cards and course reminder controls.
Home widget libraries and both bottom-navigation variants use the same account policy.
Direct routes and academic requests are also gated; cached timetable widgets show
an unavailable message. Campus-card/Wisdom services, payments, lost-and-found,
school calendar, resources, weather and separately authenticated Chaoxing remain.

Validation: :app:testDebugUnitTest passed 260 tests (zero failures/errors/skips)
and :app:assembleDebug passed. The final Debug APK was installed in-place on MEIZU 21.
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
terms do not force a new current-week prompt; their calendar can be configured with
the same edit button. Undergraduate week calculation and controls remain unchanged.
Course reminders and home current-course cards remain disabled; only the shared
timetable page has been connected to GMIS in this change.

GMIS room labels omit the repeated （江淮） campus tag. The observed building names
教学主楼、教学主楼北阶、教学主楼二楼阶梯 display as 主楼、主楼北阶、主楼二阶,
with room numbers unchanged. Unknown buildings keep their full names. This
normalization is applied only to GMIS course data; undergraduate locations keep
their existing formatter.

WebVPN was used only for read-only investigation. Its locally supplied cookies
were kept in memory and sent only to wvpn.ahu.edu.cn, never to the native app or
another host. Tests use fictional courses and an independently generated AES vector.
