# Emülatör kararı (VPS)
- CPU: 6x AMD EPYC, RAM: 11Gi (boş ~2.4Gi), disk boş 159G.
- `/dev/kvm` YOK → donanım hızlandırma imkansız; x86_64 imaj TCG ile
  dakikada kare hızında çalışır, 2.4Gi RAM'de boot/timeout kaçınılmaz.
- Sonuç: emülatör KURULMUYOR. `connectedDebugAndroidTest` ve video kanıtları
  cihaz gerektirir olarak işaretli.
- Telafi: JVM unit 28/28 + lint + assembleDebug + Robolectric denemesi (aşağıda).
