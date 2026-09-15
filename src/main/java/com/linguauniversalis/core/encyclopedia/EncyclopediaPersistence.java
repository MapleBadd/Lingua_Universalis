package com.linguauniversalis.core.encyclopedia;

import com.linguauniversalis.core.encyclopedia.Encyclopedia.Kind;
import com.linguauniversalis.core.encyclopedia.Encyclopedia.PlayerEncyclopedia;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumSet;

/**
 * {@link PlayerEncyclopedia} 的二进制编解码（按玩家持久化，纯 Java）。
 *
 * <p>格式：version + 物种数 + 每项（物种 id UTF + 解锁位图字节）。
 * 位图按 {@link Kind#ordinal()}（<=7，当前 5 种）。
 */
public final class EncyclopediaPersistence {
    public static final byte VERSION = 1;

    private EncyclopediaPersistence() {
    }

    public static byte[] encode(PlayerEncyclopedia book) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeByte(VERSION);
            out.writeInt(book.sightedCount());
            for (String speciesId : book.sightedSpecies()) {
                out.writeUTF(speciesId);
                EnumSet<Kind> kinds = EnumSet.copyOf(book.knownKinds(speciesId));
                out.writeByte(kinds.isEmpty() ? 0 : kinds.stream().mapToInt(Kind::ordinal)
                        .reduce(0, (acc, ord) -> acc | (1 << ord)));
            }
        } catch (IOException impossible) {
            throw new IllegalStateException("encode to ByteArrayOutputStream cannot fail", impossible);
        }
        return bos.toByteArray();
    }

    public static PlayerEncyclopedia decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported Encyclopedia version: " + version);
            }
            PlayerEncyclopedia book = new PlayerEncyclopedia();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String speciesId = in.readUTF();
                int mask = in.readByte() & 0xFF;
                book.recordSighting(speciesId);
                for (Kind kind : Kind.values()) {
                    if ((mask & (1 << kind.ordinal())) != 0) {
                        book.add(speciesId, kind);
                    }
                }
            }
            return book;
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed Encyclopedia data", e);
        }
    }
}
