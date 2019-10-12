/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.compress;

import org.apache.lucene.util.LineFileDocs;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.test.ESTestCase;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import java.util.Date;
import java.text.SimpleDateFormat;

/**
 * Test streaming compression (e.g. used for recovery)
 */
public class DeflateCompressTests extends ESTestCase {

    //private final Compressor compressor = new DeflateCompressor();


   private final Compressor compressor = new QatCompressor();
   //add by zj
   SimpleDateFormat def = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss a");
   Date date = new Date();


   public static byte[] longToBytes(long number){
       long temp = number;
       byte[] b =  new byte[8];
       for(int i = 0 ; i < b.length; i++){
           b[i] = new Long(temp & 0xff).byteValue();
           temp = temp >> 8 ;
       }

       return b;

   }

   public void testFixed() throws IOException{

       System.out.println("-------------------in testFixed()-----" +  def.format(date) + "---------------------------");

       // read file to bytes
       File file = new File("/home/sparkuser/Downloads/NOTICE.txt");
       InputStream input = new FileInputStream(file);
       byte[] bytes = new byte[input.available()];
       input.read(bytes);
       input.close();

       System.out.println("-----------------the size of the bytes is " + bytes.length + "---------------------" + def.format(date) );
       System.out.println("--------------the content in byt is " + Arrays.toString(bytes) + "----------------at " + def.format(date) );

       for (int i = 0; i < 10; i++) {

          // long test = 1112233;
         //  byte bytes[] = longToBytes(test);
        //   System.out.println("------------------the content in bytes is " + Arrays.toString(bytes) + "--------------------at " + def.format(new Date()) + "---------------------------" );

           doTest(bytes);
           System.out.println("------------------------test in--------------------------------" + def.format(date) + "---------------------------" );
       }


   }



    public void testRandom() throws IOException {



        System.out.println("-------------------in testRandom()-----" +  def.format(new Date()) + "---------------------------");
        Random r = random();
        System.out.println("-----------random is -------------" + r + "-------------------");
        for (int i = 0; i < 10; i++) {
            byte bytes[] = new byte[TestUtil.nextInt(r, 1, 100000)];
            //byte bytes[] = new byte[TestUtil.nextInt(r, 1, 10)];
            r.nextBytes(bytes);
            System.out.println("-----------after r.nextBytes(bytes);  r is -------------" + r + "-------------------");
            System.out.println("------------------the content in bytes is " + Arrays.toString(bytes) + "--------------------at " + def.format(new Date()) + "---------------------------" );

            doTest(bytes);
            System.out.println("------------------------test in--------------------------------");
        }
    }
    public void testRandomThreads() throws Exception {
        final Random r = random();
        int threadCount = TestUtil.nextInt(r, 2, 6);
        Thread[] threads = new Thread[threadCount];
        final CountDownLatch startingGun = new CountDownLatch(1);
        for (int tid=0; tid < threadCount; tid++) {
            final long seed = r.nextLong();
            threads[tid] = new Thread() {
                @Override
                public void run() {
                    try {
                        Random r = new Random(seed);
                        startingGun.await();
                        for (int i = 0; i < 10; i++) {
                            byte bytes[] = new byte[TestUtil.nextInt(r, 1, 100000)];
                            r.nextBytes(bytes);
                            doTest(bytes);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            threads[tid].start();
        }
        startingGun.countDown();
        for (Thread t : threads) {
            t.join();
        }
    }

    public void testLineDocs() throws IOException {
        Random r = random();
        LineFileDocs lineFileDocs = new LineFileDocs(r);
        for (int i = 0; i < 10; i++) {
            int numDocs = TestUtil.nextInt(r, 1, 200);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (int j = 0; j < numDocs; j++) {
                String s = lineFileDocs.nextDoc().get("body");
                bos.write(s.getBytes(StandardCharsets.UTF_8));
            }
            doTest(bos.toByteArray());
        }
        lineFileDocs.close();
    }

    public void testLineDocsThreads() throws Exception {
        final Random r = random();
        int threadCount = TestUtil.nextInt(r, 2, 6);
        Thread[] threads = new Thread[threadCount];
        final CountDownLatch startingGun = new CountDownLatch(1);
        for (int tid=0; tid < threadCount; tid++) {
            final long seed = r.nextLong();
            threads[tid] = new Thread() {
                @Override
                public void run() {
                    try {
                        Random r = new Random(seed);
                        startingGun.await();
                        LineFileDocs lineFileDocs = new LineFileDocs(r);
                        for (int i = 0; i < 10; i++) {
                            int numDocs = TestUtil.nextInt(r, 1, 200);
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            for (int j = 0; j < numDocs; j++) {
                                String s = lineFileDocs.nextDoc().get("body");
                                bos.write(s.getBytes(StandardCharsets.UTF_8));
                            }
                            doTest(bos.toByteArray());
                        }
                        lineFileDocs.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            threads[tid].start();
        }
        startingGun.countDown();
        for (Thread t : threads) {
            t.join();
        }
    }

    public void testRepetitionsL() throws IOException {
        Random r = random();
        for (int i = 0; i < 10; i++) {
            int numLongs = TestUtil.nextInt(r, 1, 10000);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            long theValue = r.nextLong();
            for (int j = 0; j < numLongs; j++) {
                if (r.nextInt(10) == 0) {
                    theValue = r.nextLong();
                }
                bos.write((byte) (theValue >>> 56));
                bos.write((byte) (theValue >>> 48));
                bos.write((byte) (theValue >>> 40));
                bos.write((byte) (theValue >>> 32));
                bos.write((byte) (theValue >>> 24));
                bos.write((byte) (theValue >>> 16));
                bos.write((byte) (theValue >>> 8));
                bos.write((byte) theValue);
            }
            doTest(bos.toByteArray());
        }
    }

    public void testRepetitionsLThreads() throws Exception {
        final Random r = random();
        int threadCount = TestUtil.nextInt(r, 2, 6);
        Thread[] threads = new Thread[threadCount];
        final CountDownLatch startingGun = new CountDownLatch(1);
        for (int tid=0; tid < threadCount; tid++) {
            final long seed = r.nextLong();
            threads[tid] = new Thread() {
                @Override
                public void run() {
                    try {
                        Random r = new Random(seed);
                        startingGun.await();
                        for (int i = 0; i < 10; i++) {
                            int numLongs = TestUtil.nextInt(r, 1, 10000);
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            long theValue = r.nextLong();
                            for (int j = 0; j < numLongs; j++) {
                                if (r.nextInt(10) == 0) {
                                    theValue = r.nextLong();
                                }
                                bos.write((byte) (theValue >>> 56));
                                bos.write((byte) (theValue >>> 48));
                                bos.write((byte) (theValue >>> 40));
                                bos.write((byte) (theValue >>> 32));
                                bos.write((byte) (theValue >>> 24));
                                bos.write((byte) (theValue >>> 16));
                                bos.write((byte) (theValue >>> 8));
                                bos.write((byte) theValue);
                            }
                            doTest(bos.toByteArray());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            threads[tid].start();
        }
        startingGun.countDown();
        for (Thread t : threads) {
            t.join();
        }
    }

    public void testRepetitionsI() throws IOException {
        Random r = random();
        for (int i = 0; i < 10; i++) {
            int numInts = TestUtil.nextInt(r, 1, 20000);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int theValue = r.nextInt();
            for (int j = 0; j < numInts; j++) {
                if (r.nextInt(10) == 0) {
                    theValue = r.nextInt();
                }
                bos.write((byte) (theValue >>> 24));
                bos.write((byte) (theValue >>> 16));
                bos.write((byte) (theValue >>> 8));
                bos.write((byte) theValue);
            }
            doTest(bos.toByteArray());
        }
    }

    public void testRepetitionsIThreads() throws Exception {
        final Random r = random();
        int threadCount = TestUtil.nextInt(r, 2, 6);
        Thread[] threads = new Thread[threadCount];
        final CountDownLatch startingGun = new CountDownLatch(1);
        for (int tid=0; tid < threadCount; tid++) {
            final long seed = r.nextLong();
            threads[tid] = new Thread() {
                @Override
                public void run() {
                    try {
                        Random r = new Random(seed);
                        startingGun.await();
                        for (int i = 0; i < 10; i++) {
                            int numInts = TestUtil.nextInt(r, 1, 20000);
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            int theValue = r.nextInt();
                            for (int j = 0; j < numInts; j++) {
                                if (r.nextInt(10) == 0) {
                                    theValue = r.nextInt();
                                }
                                bos.write((byte) (theValue >>> 24));
                                bos.write((byte) (theValue >>> 16));
                                bos.write((byte) (theValue >>> 8));
                                bos.write((byte) theValue);
                            }
                            doTest(bos.toByteArray());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            threads[tid].start();
        }
        startingGun.countDown();
        for (Thread t : threads) {
            t.join();
        }
    }

    public void testRepetitionsS() throws IOException {
        Random r = random();
        for (int i = 0; i < 10; i++) {
            int numShorts = TestUtil.nextInt(r, 1, 40000);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            short theValue = (short) r.nextInt(65535);
            for (int j = 0; j < numShorts; j++) {
                if (r.nextInt(10) == 0) {
                    theValue = (short) r.nextInt(65535);
                }
                bos.write((byte) (theValue >>> 8));
                bos.write((byte) theValue);
            }
            doTest(bos.toByteArray());
        }
    }

    public void testMixed() throws IOException {
        Random r = random();
        LineFileDocs lineFileDocs = new LineFileDocs(r);
        for (int i = 0; i < 2; ++i) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int prevInt = r.nextInt();
            long prevLong = r.nextLong();
            while (bos.size() < 400000) {
                switch (r.nextInt(4)) {
                case 0:
                    addInt(r, prevInt, bos);
                    break;
                case 1:
                    addLong(r, prevLong, bos);
                    break;
                case 2:
                    addString(lineFileDocs, bos);
                    break;
                case 3:
                    addBytes(r, bos);
                    break;
                default:
                    throw new IllegalStateException("Random is broken");
                }
            }
            doTest(bos.toByteArray());
        }
    }

    private void addLong(Random r, long prev, ByteArrayOutputStream bos) {
        long theValue = prev;
        if (r.nextInt(10) != 0) {
            theValue = r.nextLong();
        }
        bos.write((byte) (theValue >>> 56));
        bos.write((byte) (theValue >>> 48));
        bos.write((byte) (theValue >>> 40));
        bos.write((byte) (theValue >>> 32));
        bos.write((byte) (theValue >>> 24));
        bos.write((byte) (theValue >>> 16));
        bos.write((byte) (theValue >>> 8));
        bos.write((byte) theValue);
    }

    private void addInt(Random r, int prev, ByteArrayOutputStream bos) {
        int theValue = prev;
        if (r.nextInt(10) != 0) {
            theValue = r.nextInt();
        }
        bos.write((byte) (theValue >>> 24));
        bos.write((byte) (theValue >>> 16));
        bos.write((byte) (theValue >>> 8));
        bos.write((byte) theValue);
    }

    private void addString(LineFileDocs lineFileDocs, ByteArrayOutputStream bos) throws IOException {
        String s = lineFileDocs.nextDoc().get("body");
        bos.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private void addBytes(Random r, ByteArrayOutputStream bos) throws IOException {
        byte bytes[] = new byte[TestUtil.nextInt(r, 1, 10000)];
        r.nextBytes(bytes);
        bos.write(bytes);
    }

    public void testRepetitionsSThreads() throws Exception {
        final Random r = random();
        int threadCount = TestUtil.nextInt(r, 2, 6);
        Thread[] threads = new Thread[threadCount];
        final CountDownLatch startingGun = new CountDownLatch(1);
        for (int tid=0; tid < threadCount; tid++) {
            final long seed = r.nextLong();
            threads[tid] = new Thread() {
                @Override
                public void run() {
                    try {
                        Random r = new Random(seed);
                        startingGun.await();
                        for (int i = 0; i < 10; i++) {
                            int numShorts = TestUtil.nextInt(r, 1, 40000);
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            short theValue = (short) r.nextInt(65535);
                            for (int j = 0; j < numShorts; j++) {
                                if (r.nextInt(10) == 0) {
                                    theValue = (short) r.nextInt(65535);
                                }
                                bos.write((byte) (theValue >>> 8));
                                bos.write((byte) theValue);
                            }
                            doTest(bos.toByteArray());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            threads[tid].start();
        }
        startingGun.countDown();
        for (Thread t : threads) {
            t.join();
        }
    }

    private void doTest(byte bytes[]) throws IOException {

        ByteBuffer bb = ByteBuffer.wrap(bytes);
        StreamInput rawIn = new ByteBufferStreamInput(bb);
        System.out.println("----------------rawIn is ----------------------" + rawIn + "\n-----------------------at ----" + def.format(new Date()) + "--------------------");
        Compressor c = compressor;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStreamStreamOutput rawOs = new OutputStreamStreamOutput(bos);
        System.out.println("----------------------rawOs is ---------------------------------" + rawOs + "--------------------at " + def.format(new Date()) + "---------------------------");
        StreamOutput os = c.streamOutput(rawOs);
        System.out.println("-------------------after c.streamOutput(rawOs)   the os is ---------------------------------" + os + "--------------------at " + def.format(new Date()) + "---------------------------");


       // int bufferSize = 78;
       // int prepadding = 5;
        //int postpadding = 5;

        Random r = random();
        int bufferSize = r.nextBoolean() ? 65535 : TestUtil.nextInt(random(), 1, 70000);
        System.out.println("--------------------------bufferSize is --------------------------" + bufferSize + "--------------------at " + def.format(new Date()) + "---------------------------");

        int prepadding = r.nextInt(70000);
        System.out.println("--------------------------prepadding is --------------------------" + prepadding + "--------------------at " + def.format(new Date()) + "---------------------------");

        int postpadding = r.nextInt(70000);
        System.out.println("-------------------------- postpadding is --------------------------" +  postpadding + "--------------------at " + def.format(new Date()) + "---------------------------");

        byte buffer[] = new byte[prepadding + bufferSize + postpadding];
        r.nextBytes(buffer); // fill block completely with junk

        System.out.println("------------------before write   the content in buffer is " + Arrays.toString(buffer) + "--------------------at " + def.format(new Date()) + "---------------------------" );
        int len;
        while ((len = rawIn.read(buffer, prepadding, bufferSize)) != -1) {
            System.out.println("----------------len "+ len + "------------");
            os.write(buffer, prepadding, len);
            System.out.println("------------------the content in buffer is " + Arrays.toString(buffer) + "--------------------at " + def.format(new Date()) + "---------------------------" );
        }

        System.out.println("the len of the buffer " + buffer.length);
        System.out.println("------------------the content in buffer is " + Arrays.toString(buffer) + "--------------------at " + def.format(new Date()) + "---------------------------" );
        os.close();


        // write the buffer to the file
       // File file =new File("")
        //FileOutputStream fos = new FileOutputStream("/home/sparkuser/Downloads/compressedContentInBuffer.txt");
       // fos.write(buffer);
        //fos.close();

        System.out.println("the length of the os  is "+ bos.size());
        System.out.println("------------------the content in os is " + os.toString() + "--------------------at " + def.format(new Date()) + "---------------------------" );


       // String ss = "compressed size is " + bos.size()+ "\n";
        File file = new File("/home/sparkuser/Downloads/compressedContentInBuffer_1009_1.txt");
        OutputStream output = new FileOutputStream(file);
        BufferedOutputStream bufferedOutput = new BufferedOutputStream(output);
        //bufferedOutput.write(ss.getBytes());
        bufferedOutput.write(bos.toByteArray());
        bufferedOutput.close();



        rawIn.close();

        //assertEquals(1,1);

         //now we have compressed byte array

        byte compressed[] = bos.toByteArray();
        ByteBuffer bb2 = ByteBuffer.wrap(compressed);
        StreamInput compressedIn = new ByteBufferStreamInput(bb2);
        StreamInput in = c.streamInput(compressedIn);

     /*   File file1 = new File("/home/sparkuser/Downloads/uncompressedContentIn_1010_1.txt");
        OutputStream output1 = new FileOutputStream(file1);
        BufferedOutputStream bufferedOutput1 = new BufferedOutputStream(output1);
        bufferedOutput1.write(compressed);
        bufferedOutput1.close();*/



        // randomize constants again
        //bufferSize = r.nextBoolean() ? 65535 : TestUtil.nextInt(random(), 1, 70000);
       // prepadding = r.nextInt(70000);
       // postpadding = r.nextInt(70000);
        buffer = new byte[prepadding + bufferSize + postpadding];
        r.nextBytes(buffer); // fill block completely with junk

        ByteArrayOutputStream uncompressedOut = new ByteArrayOutputStream();
        while ((len = in.read(buffer, prepadding, bufferSize)) != -1) {
            uncompressedOut.write(buffer, prepadding, len);
        }
        uncompressedOut.close();


    /*    File file2 = new File("/home/sparkuser/Downloads/uncompressedContentOut_1010_1.txt");
        OutputStream output2 = new FileOutputStream(file2);
        BufferedOutputStream bufferedOutput2 = new BufferedOutputStream(output2);
        bufferedOutput2.write(uncompressedOut.toByteArray());
        bufferedOutput2.close();*/


        assertArrayEquals(bytes, uncompressedOut.toByteArray());
    }





}
