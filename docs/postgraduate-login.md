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

GMIS validation requires an HTTPS GMIS page, account identity, a logout control,
and student navigation. Login/error/password/role-selection/teacher pages are
rejected. These markers are conservative: an authenticated page with a different
layout remains unconfirmed instead of silently classifying the account.
The authenticated GMIS student page has not yet been captured from a real account;
its exact layout and the full live flow still require device verification.
Diagnostic logs use the AcademicLogin tag and omit identifiers, tickets and HTML.

Postgraduate accounts hide undergraduate timetable, grades/GPA, exams, evaluation,
free classrooms, semester settings, home course cards and course reminder controls.
Home widget libraries and both bottom-navigation variants use the same account policy.
Direct routes and academic requests are also gated; cached timetable widgets show
an unavailable message. Campus-card/Wisdom services, payments, lost-and-found,
school calendar, resources, weather and separately authenticated Chaoxing remain.

Validation: run :app:testDebugUnitTest and :app:assembleDebug.
Manual checks: undergraduate sign-in preserves academic features; a real postgraduate
sign-in shows “研究生账号” in Settings and hides undergraduate features; restart and
switch accounts to confirm persistence and restore undergraduate features.
