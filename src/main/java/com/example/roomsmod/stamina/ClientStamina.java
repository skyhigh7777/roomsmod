package com.example.roomsmod.stamina;

/** クライアント側で受け取った最新のスタミナ（クライアント専用クラスには依存しない）。 */
public final class ClientStamina {
    private ClientStamina() {}
    public static volatile float value = Stamina.MAX;
    public static volatile boolean exhausted = false;
}
