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

Postgraduate accounts hide undergraduate timetable, grades/GPA, exams, evaluation,
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
