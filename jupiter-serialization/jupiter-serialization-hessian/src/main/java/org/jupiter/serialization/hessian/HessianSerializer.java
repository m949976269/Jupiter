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

package org.jupiter.serialization.hessian;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import org.jupiter.common.util.ExceptionUtil;
import org.jupiter.common.util.internal.InternalThreadLocal;
import org.jupiter.common.util.internal.UnsafeReferenceFieldUpdater;
import org.jupiter.common.util.internal.UnsafeUpdater;
import org.jupiter.serialization.InputBuf;
import org.jupiter.serialization.OutputBuf;
import org.jupiter.serialization.Serializer;
import org.jupiter.serialization.SerializerType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Hessian的序列化/反序列化实现
 *
 * jupiter
 * org.jupiter.serialization.hessian
 *
 * @author jiachun.fjc
 */
public class HessianSerializer extends Serializer {

    private static final UnsafeReferenceFieldUpdater<ByteArrayOutputStream, byte[]> bufUpdater =
            UnsafeUpdater.newReferenceFieldUpdater(ByteArrayOutputStream.class, "buf");

    // 目的是复用 ByteArrayOutputStream 中的 byte[]
    private static final InternalThreadLocal<ByteArrayOutputStream> bufThreadLocal = new InternalThreadLocal<ByteArrayOutputStream>() {

        @Override
        protected ByteArrayOutputStream initialValue() {
            return new ByteArrayOutputStream(DEFAULT_BUF_SIZE);
        }
    };

    @Override
    public byte code() {
        return SerializerType.HESSIAN.value();
    }

    @Override
    public <T> OutputBuf writeObject(OutputBuf outputBuf, T obj) {
        Hessian2Output hOutput = new Hessian2Output(outputBuf.outputStream());
        try {
            hOutput.writeObject(obj);
            hOutput.flush();
            return outputBuf;
        } catch (IOException e) {
            ExceptionUtil.throwException(e);
        } finally {
            try {
                hOutput.close();
            } catch (IOException ignored) {}
        }
        return null; // never get here
    }

    @Override
    public <T> byte[] writeObject(T obj) {
        ByteArrayOutputStream buf = bufThreadLocal.get();
        Hessian2Output hOutput = new Hessian2Output(buf);
        try {
            hOutput.writeObject(obj);
            hOutput.flush();
            return buf.toByteArray();
        } catch (IOException e) {
            ExceptionUtil.throwException(e);
        } finally {
            try {
                hOutput.close();
            } catch (IOException ignored) {}

            buf.reset(); // for reuse

            // 防止hold过大的内存块一直不释放
            assert bufUpdater != null;
            if (bufUpdater.get(buf).length > MAX_CACHED_BUF_SIZE) {
                bufUpdater.set(buf, new byte[DEFAULT_BUF_SIZE]);
            }
        }
        return null; // never get here
    }

    @Override
    public <T> T readObject(InputBuf inputBuf, Class<T> clazz) {
        Hessian2Input hInput = new Hessian2Input(inputBuf.inputStream());
        try {
            return clazz.cast(hInput.readObject(clazz));
        } catch (IOException e) {
            ExceptionUtil.throwException(e);
        } finally {
            try {
                hInput.close();
            } catch (IOException ignored) {}

            inputBuf.release();
        }
        return null; // never get here
    }

    @Override
    public <T> T readObject(byte[] bytes, int offset, int length, Class<T> clazz) {
        Hessian2Input hInput = new Hessian2Input(new ByteArrayInputStream(bytes, offset, length));
        try {
            return clazz.cast(hInput.readObject(clazz));
        } catch (IOException e) {
            ExceptionUtil.throwException(e);
        } finally {
            try {
                hInput.close();
            } catch (IOException ignored) {}
        }
        return null; // never get here
    }

    @Override
    public String toString() {
        return "hessian:(code=" + code() + ")";
    }
}
