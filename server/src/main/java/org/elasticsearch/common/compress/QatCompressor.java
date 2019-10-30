package org.elasticsearch.common.compress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.lang.*;

import com.intel.qat.es.QatInputStream;
import com.intel.qat.es.QatOutputStream;

public class QatCompressor implements Compressor{
    //add log to identify whether using qat
    private final Logger logger = LogManager.getLogger(QatCompressor.class);

    private static final byte[] HEADER = new byte[]{'Q', 'A', 'T', '\0'};
    // 3 is a good trade-off between speed and compression ratio
    private static final int LEVEL = 3;
    // We use buffering on the input and output of in/def-laters in order to
    // limit the number of JNI calls
   // private static final int BUFFER_SIZE = 4096;
    private static final int BUFFER_SIZE = 8192;

    @Override
    public boolean isCompressed(BytesReference bytes){
        logger.info("--> go into the isCompressed function");
        if (bytes.length() < HEADER.length) {
            return false;
        }
        for (int i = 0; i < HEADER.length; ++i) {
            if (bytes.get(i) != HEADER[i]) {
                return false;
            }
        }
        return true;

    }

    @Override
    public StreamInput streamInput(StreamInput in) throws IOException{
        logger.info("--> go into the streamInput function");
//        /*write to txt */
//        File writename = new File("/home/sparkuser/Downloads/elasticsearch/testToCompressQat_1030.txt\n");
//        writename.createNewFile(); //
//        BufferedWriter out = new BufferedWriter(new FileWriter(writename));
//        out.write("\r\n"); //
//        out.flush();
//        out.close(); //

        final byte[] headerBytes = new byte[HEADER.length];
        int len = 0;
        while (len < headerBytes.length) {
            final int read = in.read(headerBytes, len, headerBytes.length - len);//store the data in the in with the length headerBytes.length - len to the headerBytes start from len
            if (read == -1) {
                break;
            }
            len += read;
        }
        if (len != HEADER.length || Arrays.equals(headerBytes, HEADER) == false) {
            throw new IllegalArgumentException("Input stream is not compressed with QAT!");
        }


        final boolean useNativeBuffer = false;
        //final QatInputStream qatInputStream = new QatInputStream(in);

        QatInputStream qatInputStream  = new QatInputStream(in,BUFFER_SIZE,useNativeBuffer);

        //final boolean nowrap = true;
        //final Inflater inflater = new Inflater(nowrap);  // zhi chi ZIP ya suo  chuang jian yi ge  xin de  jie ya suo  cheng xu
        //InputStream decompressedIn = new InflaterInputStream(in, inflater, BUFFER_SIZE);// shi yong  zhi ding de jie ya suo cheng xu  chuang jian xin de shu ru liu  shuruliu  jieyasuo chengxu  huanchongqu daxiao

        InputStream decompressedIn = new BufferedInputStream(qatInputStream, BUFFER_SIZE);
        return new InputStreamStreamInput(decompressedIn) {// chaung jian yi ge  meiyou daxiao xianzhi de  shuruliu
            final AtomicBoolean closed = new AtomicBoolean(false);

            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    if (closed.compareAndSet(false, true)) {
                        // important to release native memory
                        //inflater.end();
                        qatInputStream.close();
                    }
                }
            }
        };


    }

    /**
     * Creates a new stream output that compresses the contents and writes to the provided stream
     * output. Closing the returned {@link StreamOutput} will close the provided stream output.
     */
    @Override
    public StreamOutput streamOutput(StreamOutput out) throws IOException{
        logger.info("--> go into the streamOutput function");
        out.writeBytes(HEADER);
       // final boolean nowrap = true;
       // final Deflater deflater = new Deflater(LEVEL, nowrap); //
       // final boolean syncFlush = true;
        /** Zhangjie   20190731
         *  DeflaterOutputStream(out, deflater, BUFFER_SIZE, syncFlush)
         * *使用指定的压缩器，缓冲区大小和刷新模式创建新的输出流。
         * ***/

        final boolean useNativeBuffer = false;
        QatOutputStream qatOutputStream = new QatOutputStream(out,BUFFER_SIZE,useNativeBuffer);

        //DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(out, deflater, BUFFER_SIZE, syncFlush);

        /** Zhangjie 20190731
         *   BufferedOutputStream(deflaterOutputStream, BUFFER_SIZE)
         *   创建新的缓冲输出流，以使用指定的缓冲区大小将数据写入指定的基础输出流。
         *  @param out  基础输出流。
         *  @param size 缓冲区大小。
         *  @exception IllegalArgumentException如果size＆lt; = 0。
         *
         * **/
        OutputStream compressedOut = new BufferedOutputStream(qatOutputStream, BUFFER_SIZE);
        //OutputStream compressedOut = new BufferedOutputStream(deflaterOutputStream, BUFFER_SIZE);

        return new OutputStreamStreamOutput(compressedOut) {
            final AtomicBoolean closed = new AtomicBoolean(false);

            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    if (closed.compareAndSet(false, true)) {
                        // important to release native memory
                        //deflater.end();
                        qatOutputStream.close();
                    }
                }
            }
        };
    }



}
