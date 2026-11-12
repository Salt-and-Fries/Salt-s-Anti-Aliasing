package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads actual SPIR-V bindings and member offsets, including std140 padding and matrices. */
public record SpirvReflection(List<Descriptor> descriptors, int colorOutputs) {
    public record Member(String name, int offset, int bytes) {}
    public record Descriptor(String name, int set, int binding, Kind kind, int count,
                             int bytes, List<Member> members) {}
    public enum Kind { SAMPLED_IMAGE, STORAGE_IMAGE, UNIFORM_BUFFER, STORAGE_BUFFER }

    private record Type(int op, int[] args) {}
    private record Variable(int type, int id, int storage) {}

    public static SpirvReflection read(byte[] binary) {
        if (binary.length < 20 || binary.length % 4 != 0) throw new IllegalArgumentException("Invalid SPIR-V size");
        var words = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        if (words.get(0) != 0x07230203) throw new IllegalArgumentException("Invalid SPIR-V magic");
        Map<Integer, String> names = new HashMap<>();
        Map<Long, String> memberNames = new HashMap<>();
        Map<Long, Integer> memberOffsets = new HashMap<>();
        Map<Long, Integer> matrixStrides = new HashMap<>();
        Map<Integer, Integer> sets = new HashMap<>(), bindings = new HashMap<>(), locations = new HashMap<>();
        Map<Integer, Integer> strides = new HashMap<>(), constants = new HashMap<>();
        Map<Integer, Type> types = new HashMap<>();
        List<Variable> variables = new ArrayList<>();
        for (int p = 5; p < words.limit();) {
            int header = words.get(p), count = header >>> 16, op = header & 0xffff;
            if (count < 1 || p + count > words.limit()) throw new IllegalArgumentException("Invalid SPIR-V instruction at " + p);
            int[] args = new int[count - 1];
            for (int i = 0; i < args.length; i++) args[i] = words.get(p + i + 1);
            switch (op) {
                case 5 -> names.put(args[0], string(args, 1));
                case 6 -> memberNames.put(key(args[0], args[1]), string(args, 2));
                case 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32 ->
                        types.put(args[0], new Type(op, Arrays.copyOfRange(args, 1, args.length)));
                case 43 -> { if (args.length >= 3) constants.put(args[1], args[2]); }
                case 59 -> variables.add(new Variable(args[0], args[1], args[2]));
                case 71 -> {
                    if (args.length >= 3) switch (args[1]) {
                        case 6 -> strides.put(args[0], args[2]);
                        case 30 -> locations.put(args[0], args[2]);
                        case 33 -> bindings.put(args[0], args[2]);
                        case 34 -> sets.put(args[0], args[2]);
                    }
                }
                case 72 -> {
                    if (args.length >= 4) {
                        if (args[2] == 35) memberOffsets.put(key(args[0], args[1]), args[3]);
                        if (args[2] == 7) matrixStrides.put(key(args[0], args[1]), args[3]);
                    }
                }
            }
            p += count;
        }
        List<Descriptor> descriptors = new ArrayList<>();
        int outputs = 0;
        for (Variable variable : variables) {
            if (variable.storage == 3 && locations.containsKey(variable.id))
                outputs = Math.max(outputs, locations.get(variable.id) + 1);
            if (!bindings.containsKey(variable.id)) continue;
            Type pointer = require(types, variable.type);
            int targetId = pointer.args[1], elements = 1;
            Type target = require(types, targetId);
            if (target.op == 28) {
                elements = constants.getOrDefault(target.args[1], 0);
                targetId = target.args[0];
                target = require(types, targetId);
            }
            Kind kind;
            List<Member> members = new ArrayList<>();
            int bytes = 0;
            String name = names.getOrDefault(variable.id, "");
            if (target.op == 30) {
                kind = variable.storage == 12 ? Kind.STORAGE_BUFFER : Kind.UNIFORM_BUFFER;
                name = names.getOrDefault(targetId, name);
                for (int member = 0; member < target.args.length; member++) {
                    long key = key(targetId, member);
                    Integer offset = memberOffsets.get(key);
                    if (offset == null) throw new IllegalArgumentException("Block member has no offset: " + name);
                    int size = size(target.args[member], types, constants, strides, matrixStrides.get(key));
                    members.add(new Member(memberNames.getOrDefault(key, "member" + member), offset, size));
                    bytes = Math.max(bytes, Math.addExact(offset, size));
                }
                bytes = (bytes + 15) & ~15;
            } else if (target.op == 27) kind = Kind.SAMPLED_IMAGE;
            else if (target.op == 25) kind = target.args[5] == 2 ? Kind.STORAGE_IMAGE : Kind.SAMPLED_IMAGE;
            else throw new IllegalArgumentException("Unsupported descriptor type " + target.op);
            descriptors.add(new Descriptor(name, sets.getOrDefault(variable.id, 0), bindings.get(variable.id),
                    kind, elements, bytes, List.copyOf(members)));
        }
        descriptors.sort(Comparator.comparingInt(Descriptor::set).thenComparingInt(Descriptor::binding));
        Set<Long> occupied = new HashSet<>();
        for (Descriptor descriptor : descriptors) {
            if (!occupied.add(key(descriptor.set, descriptor.binding)))
                throw new IllegalArgumentException("Duplicate shader descriptor binding " + descriptor);
        }
        return new SpirvReflection(List.copyOf(descriptors), outputs);
    }

    private static int size(int id, Map<Integer, Type> types, Map<Integer, Integer> constants,
                            Map<Integer, Integer> strides, Integer matrixStride) {
        Type type = require(types, id);
        return switch (type.op) {
            case 20 -> 4;
            case 21, 22 -> type.args[0] / 8;
            case 23 -> size(type.args[0], types, constants, strides, null) * type.args[1];
            case 24 -> (matrixStride != null ? matrixStride : 16) * type.args[1];
            case 28 -> strides.getOrDefault(id, 16) * constants.getOrDefault(type.args[1], 0);
            case 29 -> 0;
            default -> throw new IllegalArgumentException("Unsupported uniform member type " + type.op);
        };
    }

    private static Type require(Map<Integer, Type> types, int id) {
        Type type = types.get(id);
        if (type == null) throw new IllegalArgumentException("Missing SPIR-V type " + id);
        return type;
    }

    private static long key(int high, int low) { return ((long) high << 32) | (low & 0xffffffffL); }

    private static String string(int[] words, int start) {
        var bytes = ByteBuffer.allocate((words.length - start) * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = start; i < words.length; i++) bytes.putInt(words[i]);
        int length = 0;
        while (length < bytes.position() && bytes.get(length) != 0) length++;
        return new String(bytes.array(), 0, length, StandardCharsets.UTF_8);
    }
}
