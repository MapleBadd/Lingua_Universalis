package com.linguauniversalis.core.state;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * {@link MonsterGirlState} 的二进制编解码（服务端持久化/网络传输用，纯 Java）。
 *
 * <p>格式：version 字节 + 定长整数段 + 变长 UUID（DataOutputStream#writeUTF，允许 null 以 -1 长度表示）。
 * 由 Phase 2 适配层写入实体存档/数据组件。
 */
public final class GirlStatePersistence {
    public static final byte VERSION = 1;

    private GirlStatePersistence() {
    }

    public static byte[] encode(MonsterGirlState state) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeByte(VERSION);
            out.writeInt(state.affection());
            out.writeInt(state.mood());
            out.writeInt(state.satiety());
            out.writeInt(state.synergy());
            writeNullableString(out, state.boundPlayerUuid());
            out.writeBoolean(state.companionUnlocked());
            out.writeBoolean(state.vowed());
            out.writeBoolean(state.dormant());
            out.writeBoolean(state.downed());
            out.writeLong(state.downedLockTicks());
        } catch (IOException impossible) {
            throw new IllegalStateException("encode to ByteArrayOutputStream cannot fail", impossible);
        }
        return bos.toByteArray();
    }

    public static MonsterGirlState decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported GirlState version: " + version);
            }
            MonsterGirlState state = new MonsterGirlState();
            state.setAffection(in.readInt());
            state.setMood(in.readInt());
            state.setSatiety(in.readInt());
            state.setSynergy(in.readInt());
            state.setBoundPlayerUuid(readNullableString(in));
            state.setCompanionUnlocked(in.readBoolean());
            state.setVowed(in.readBoolean());
            state.setDormant(in.readBoolean());
            state.setDowned(in.readBoolean());
            state.setDownedLockTicks(in.readLong());
            return state;
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed GirlState data", e);
        }
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(1);
            out.writeUTF(value);
        }
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        int marker = in.readInt();
        if (marker < 0) {
            return null;
        }
        return in.readUTF();
    }
}
