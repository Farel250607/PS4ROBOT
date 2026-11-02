# PS4 Robot — build APK di cloud tanpa Android Studio

Project Android Kotlin untuk menerima input stik/gamepad yang sudah dipasangkan ke HP Android dan mengirim perintah HTTP ke ESP32-C3 melalui Wi-Fi.

## Firmware ESP32-C3
Gunakan firmware ESP32-C3 yang membuat access point:
- SSID: `PS4-ROBOT`
- Password: `robot123`
- Endpoint: `http://192.168.4.1/cmd?dir=forward|backward|left|right|stop`

Firmware ESP32 harus punya endpoint `/cmd` seperti pada instruksi di percakapan.

## Membuat APK tanpa Android Studio
1. Masuk ke https://github.com dan login/buat akun.
2. Buat repository baru bernama `PS4Robot`, pilih Public atau Private.
3. Upload seluruh isi ZIP ini ke repository (bukan folder pembungkus `PS4RobotCloud`). Pastikan `.github/workflows/build-apk.yml` ikut terunggah.
4. Buka tab **Actions** repository dan pilih workflow **Build PS4 Robot APK**. Tekan **Run workflow**. Jika workflow belum muncul, pastikan file YAML ada di branch `main`.
5. Tunggu proses selesai, buka run yang hijau, lalu unduh artifact **PS4Robot-debug-apk**.
6. Ekstrak ZIP artifact dan pasang `app-debug.apk` di HP. Android mungkin meminta izin memasang aplikasi dari browser/file manager.

## Cara pakai
1. Pair stik PS4 (SHARE + PS sampai lampu berkedip) di Bluetooth HP.
2. Sambungkan Wi-Fi HP ke `PS4-ROBOT` dengan password `robot123`. Jika diminta, tetap sambungkan walau tanpa internet.
3. Buka PS4 Robot dan coba D-pad atau joystick kiri.

## Catatan
- APK ini adalah build debug untuk pengujian, bukan aplikasi Play Store.
- Input gamepad Android dapat berbeda menurut perangkat/firmware; perlu pengujian langsung dengan stik yang dipakai.
- Firmware di atas hanya menguji komunikasi perintah. Belum mengendalikan motor secara fisik; perlu driver motor dan program output motor yang sesuai.
