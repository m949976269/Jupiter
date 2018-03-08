/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jupiter.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.FastInput;
import com.esotericsoftware.kryo.io.FastOutput;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.jupiter.common.concurrent.collection.ConcurrentSet;
import org.jupiter.common.util.internal.InternalThreadLocal;
import org.jupiter.serialization.InputBuf;
import org.jupiter.serialization.OutputBuf;
import org.jupiter.serialization.Serializer;
import org.jupiter.serialization.SerializerType;
import org.jupiter.serialization.kryo.buffer.InputFactory;
import org.jupiter.serialization.kryo.buffer.OutputFactory;
import org.objenesis.strategy.StdInstantiatorStrategy;

/**
 * Kryo的序列化/反序列化实现.
 *
 * 要注意的是关掉了对存在循环引用的类型的支持, 如果一定要序列化/反序列化循环引用的类型,
 * 可以通过 {@link #setJavaSerializer(Class)} 设置该类型使用Java的序列化/反序列化机制,
 * 对性能有一点影响, 但只是影响一个'点', 不影响'面'.
 *
 * jupiter
 * org.jupiter.serialization.kryo
 *
 * @author jiachun.fjc
 */
public class KryoSerializer extends Serializer {

    private static ConcurrentSet<Class<?>> useJavaSerializerTypes = new ConcurrentSet<>();

    static {
        useJavaSerializerTypes.add(Throwable.class);
    }

    private static final InternalThreadLocal<Kryo> kryoThreadLocal = new InternalThreadLocal<Kryo>() {

        @Override
        protected Kryo initialValue() throws Exception {
            Kryo kryo = new Kryo();
            for (Class<?> type : useJavaSerializerTypes) {
                kryo.addDefaultSerializer(type, JavaSerializer.class);
            }
            kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
            kryo.setRegistrationRequired(false);
            kryo.setReferences(false);
            return kryo;
        }
    };

    // 目的是复用 Output 中的 byte[]
    private static final InternalThreadLocal<Output> outputBytesThreadLocal = new InternalThreadLocal<Output>() {

        @Override
        protected Output initialValue() {
            return new FastOutput(DEFAULT_BUF_SIZE, -1);
        }
    };

    /**
     * Serializes {@code type}'s objects using Java's built in serialization mechanism,
     * note that this is very inefficient and should be avoided if possible.
     */
    public static void setJavaSerializer(Class<?> type) {
        useJavaSerializerTypes.add(type);
    }

    @Override
    public byte code() {
        return SerializerType.KRYO.value();
    }

    @Override
    public <T> OutputBuf writeObject(OutputBuf outputBuf, T obj) {
        kryoThreadLocal
                .get()
                .writeObject(OutputFactory.getOutput(outputBuf), obj);
        return outputBuf;
    }

    @Override
    public <T> byte[] writeObject(T obj) {
        Output kOutput = outputBytesThreadLocal.get();
        try {
            kryoThreadLocal
                    .get()
                    .writeObject(kOutput, obj);
            return kOutput.toBytes();
        } finally {
            kOutput.clear();

            // 防止hold过大的内存块一直不释放
            if (kOutput.getBuffer().length > MAX_CACHED_BUF_SIZE) {
                kOutput.setBuffer(new byte[DEFAULT_BUF_SIZE], -1);
            }
        }
    }

    @Override
    public <T> T readObject(InputBuf inputBuf, Class<T> clazz) {
        try {
            return kryoThreadLocal
                    .get()
                    .readObject(InputFactory.getInput(inputBuf), clazz);
        } finally {
            inputBuf.release();
        }
    }

    @Override
    public <T> T readObject(byte[] bytes, int offset, int length, Class<T> clazz) {
        return kryoThreadLocal
                .get()
                .readObject(new FastInput(bytes, offset, length), clazz);
    }

    @Override
    public String toString() {
        return "kryo:(code=" + code() + ")";
    }
}
