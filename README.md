# İş Takip Android

Bu proje, yayımlanmış İş Takip uygulamasını Android üzerinde bağımsız uygulama görünümünde açan kurulabilir APK paketidir.

## Dahil olanlar

- Uygulama içi güvenli HTTPS görüntüleme
- Oturum çerezlerinin korunması
- Android geri tuşu desteği
- Fotoğraf ve belge seçme
- PDF/CSV gibi doğrudan ve `blob:` tabanlı raporları indirme
- Bağlantı yok ekranı ve yeniden deneme
- Dış bağlantıları uygun Android uygulamasında açma

## Derleme

GitHub Actions, `main` dalına ilk yüklemede otomatik olarak `Is-Takip-v1.0.0.apk` üretir. İş akışı ayrıca GitHub'daki **Actions → Android APK Oluştur → Run workflow** yoluyla elle başlatılabilir.

APK bir test/kurulum anahtarıyla imzalanır ve Android telefona kurulabilir. Play Store yayını için ayrıca kalıcı yayın anahtarı gerekir.
