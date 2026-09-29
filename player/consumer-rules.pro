# JNI looks up these private members by name.
-keepclassmembers class com.rockbyte.vicu.player.NativePlayerImpl {
    long nativeHandle;
    private void notifyPrepared(int, int, long, boolean);
    private void notifyPosition(long);
    private void notifyEnded();
    private void notifyError(int, java.lang.String);
    private void notifyAudioData(byte[], long, int);
    private long audioClockUs();
    private native <methods>;
}
