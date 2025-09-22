import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.IplImage;


public class TCPClient implements Runnable {
    public static boolean foundPort = false;
    public static Lock portLock = new ReentrantLock();
    public TCPClient() {}

    static boolean applicationStarted = false;

    public static void main(String[] args) {
        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        try {
            Socket tcpSocket = new Socket(hostName, portNumber);
            PrintWriter out = new PrintWriter(tcpSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

            System.out.print("Enter a name: ");
            String userInput = stdIn.readLine();
            out.println(userInput);
            String o = in.readLine();
            while (!o.equals("Connected...")) {
                System.out.println(o);
                System.out.print("Enter a username: ");
                userInput = stdIn.readLine();
                out.println(userInput);
                o = in.readLine();
            }
            System.out.println(o);

            Thread input = new Thread(() -> {
                String inp;
                try {
                    while ((inp = stdIn.readLine()) != null) {
                        out.println(inp);
                    }
                } catch (IOException ioException) {
                    System.err.println("I/O error");
                    ioException.printStackTrace();
                    System.exit(1);
                }
            });
            Thread output = new Thread(() -> {
                String outp;
                try {
                    while ((outp = in.readLine()) != null) {
                        String[] splitOut = outp.split(" ");
                        if (Objects.equals(splitOut[0], "/sendfile")) {
                            // Handle file transfer in separate thread
                            new Thread(() -> {
                                try {
                                    sendFile(splitOut[1].substring(1), Integer.parseInt(splitOut[2]));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }).start();
                        } else if (Objects.equals(splitOut[0], "/readfile")) {
                            // Handle file reception in separate thread
                            new Thread(() -> {
                                try {
                                    readFile(tcpSocket);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }).start();

                        } else if (Objects.equals(splitOut[0], "/streamfile")) {
                                new Thread(() -> {
                                    try {
                                        sendStream(splitOut[1].substring(1), Integer.parseInt(splitOut[2]), false);
                                    } catch (IOException | InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }).start();

                        } else if (Objects.equals(splitOut[0], "/streamcam")) {
                            new Thread(() -> {
                                try {
                                    sendStream(splitOut[1].substring(1), Integer.parseInt(splitOut[2]), true);
                                } catch (IOException | InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }).start();

                        } else if (Objects.equals(splitOut[0], "/readstream")) {
                                new Thread(() -> {
                                    try {
                                        readStream(tcpSocket);
                                    } catch (IOException | InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }).start();
                        } else if (outp.equals("/foundPort")) {
                            foundPort = true;
                        } else {
                            System.out.println(outp);
                        }
                    }
                } catch (IOException ioException) {
                    System.err.println("I/O error");
                    ioException.printStackTrace();
                    System.exit(1);
                }
            });

            input.start();
            output.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void sendFile(String address, int port) throws IOException {
        Socket target = new Socket(address, port);

        JFrame panel = new JFrame();
        panel.setAlwaysOnTop(true);
        JFileChooser choose = new JFileChooser();
        choose.showOpenDialog(panel);
        panel.add(choose);
        File file = choose.getSelectedFile();

        PrintWriter out = new PrintWriter(target.getOutputStream(),true);

        if (file == null) {
            out.println("/abort");
            return;
        }

        out.println(file.getName());

        byte[] buffer = new byte[8192];
        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(target.getOutputStream());
        int count;
        while ((count  = bufferedInputStream.read(buffer)) > 0) {
            bufferedOutputStream.write(buffer, 0, count);
        }
        System.out.println("Sent file: " + file.getName());
        bufferedOutputStream.close();
        bufferedInputStream.close();
        fileInputStream.close();
        target.close();
    }


    public static void readFile(Socket tcpSocket) throws IOException {

        Socket sender = getSocket(tcpSocket);

        BufferedReader in = new BufferedReader(new InputStreamReader(sender.getInputStream()));
        String fileName = in.readLine();

        if (fileName.equals("/abort")) {
            return;
        }

        File outputFile = new File (System.getProperty("user.home") + File.separator + "Downloads", fileName);
        InputStream inputStream = sender.getInputStream();
        FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
            bufferedOutputStream.write(buffer, 0, count);
        }

        System.out.println("Received file at: " + outputFile.getAbsolutePath());
        String[] imageTypes = {".jpg", ".png", ".gif", ".tif", ".bmp"};
        String[] videoTypes = {".mp4"};


        new Thread(() -> {
            for (String imageType : imageTypes) {
                if (fileName.endsWith(imageType)) {
                    try {
                        displayImage(outputFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();

        new Thread(() -> {
            for (String videoType : videoTypes) {
                if (fileName.endsWith(videoType)) {
                    try {
                        displayVideo(outputFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
        bufferedOutputStream.close();
        inputStream.close();
        sender.close();
    }

    private static Socket getSocket(Socket tcpSocket) throws IOException {
        ServerSocket receive = null;
        int i;
        for (i = 2001; i < 3000; i++) {
            try {
                receive = new ServerSocket(i);
                break;
            } catch (BindException ignored) {}
        }

        if (receive == null) throw new RuntimeException("Failed to create server, no available ports.");

        PrintWriter out = new PrintWriter(tcpSocket.getOutputStream(),true);
        portLock.lock();
        try {
            while (!foundPort) {
                out.println("/port " + i);
            }
            foundPort = false;
        } finally {
            portLock.unlock();
        }
        return receive.accept();
    }

    private static void displayImage(File file) throws IOException {
        ImageIcon img = new ImageIcon(file.getAbsolutePath());

        JFrame jframe = new JFrame();
        JLabel picture = new JLabel(img);
        jframe.add(picture);
        jframe.pack();
        jframe.setVisible(true);

    }

    public static class javaFX extends Application {

        @Override
        public void start(Stage stage) throws Exception {

            stage.setTitle("Client JavaFX Handler"); //javafx is stupid and it wont work unless you keep a background window open all the time for some reason
            stage.show();
        }

        public static void playVideo(File file) {

                Media video = new Media(file.toURI().toString());
                MediaPlayer mediaPlayer = new MediaPlayer(video);
                MediaView view = new MediaView(mediaPlayer);
                view.setFitHeight(500);
                view.setFitWidth(500);

                Button pause = new Button("Pause");
                Button resume = new Button("Resume");
                HBox hungrybox = new HBox(20, pause, resume);
                hungrybox.setAlignment(Pos.CENTER);

                pause.setOnAction(act -> mediaPlayer.pause());
                resume.setOnAction(act -> mediaPlayer.play());

                VBox root = new VBox();
                root.setAlignment(Pos.CENTER);
                root.getChildren().addAll(view, hungrybox);

                Scene scene = new Scene(root, 600, 400);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.setAlwaysOnTop(true);
                stage.setTitle(file.getName());

            stage.show();

                mediaPlayer.setOnReady(() -> {
                    mediaPlayer.play();
                });

                mediaPlayer.setOnError(() -> {
                    System.out.println("Error " + mediaPlayer.getError());
                });

                mediaPlayer.setOnEndOfMedia(() -> {
                    Platform.runLater(() -> {
                        mediaPlayer.stop();
                        root.getChildren().remove(view);
                        stage.hide();
                    });
                });
        }
    }

    private static void displayVideo(File file) throws IOException {

        if (!applicationStarted) {
            Platform.startup(() -> {
                try {
                    new javaFX().start(new Stage());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            applicationStarted = true;
        }
        Platform.runLater(() -> javaFX.playVideo(file));
    }

    public static void sendStream(String address, int port, boolean camera) throws IOException, InterruptedException {

        InetAddress targetAddress = InetAddress.getByName(address);
        DatagramSocket socket = new DatagramSocket();
        int packetSize = 1024;

        int sequenceNum = 0;

        if(camera) {
            CanvasFrame canvas = new CanvasFrame("Camera");
            canvas.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            FrameGrabber grabber = new OpenCVFrameGrabber(0);
            grabber.start();
            Frame frame = grabber.grab();

            OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
            IplImage image = converter.convert(frame);
            opencv_imgcodecs.cvSaveImage("frame.jpg", image);

            canvas.showImage(frame);
            Thread.sleep(1000);
            canvas.dispatchEvent(new WindowEvent(canvas, WindowEvent.WINDOW_CLOSING));
        }

        else {
            JFrame panel = new JFrame();
            panel.setAlwaysOnTop(true);
            JFileChooser choose = new JFileChooser();
            choose.showOpenDialog(panel);
            panel.add(choose);
            File file = choose.getSelectedFile();
            if (file == null) {
                return;
            }

            FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber(file);

            frameGrabber.start();

            boolean finished = false;

            while(!finished) {
                Frame frame = frameGrabber.grab();
                System.out.println("Frame Grabbed");
                if(frame == null) {
                    finished = true;
                }

                else {
                    Java2DFrameConverter converter = new Java2DFrameConverter();
                    BufferedImage image = converter.convert(frame);

                    if(image != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(image, "jpg", out);

                        byte[] videoData = out.toByteArray();

                        int packetsPerFrame = videoData.length / packetSize;
                        for (int i = 0; i < packetsPerFrame; i++) {
                            int cutoffByte = i * packetSize;
                            //implying we're nearing the end
                            int packetByteLength = 0;
                            if(videoData.length-cutoffByte < packetSize) {
                                packetByteLength = videoData.length - cutoffByte;
                            }
                            else {
                                packetByteLength = packetSize;
                            }

                            byte[] truncatedVideoData = new byte[packetByteLength];
                            System.arraycopy(videoData, cutoffByte, truncatedVideoData, 0, packetByteLength);


                            DatagramPacket packet = new DatagramPacket(truncatedVideoData, truncatedVideoData.length, targetAddress, port);
                            socket.send(packet);
                            sequenceNum++;
                        }
                    }
                }
            }

            String end = "THISISTHEENDOFTHESTREAM";
            byte[] eof = end.getBytes();
            DatagramPacket endPacket = new DatagramPacket(eof,eof.length, targetAddress, port);
            for(int i=0;i<10;i++) {
                socket.send(endPacket);
            }
            frameGrabber.stop();

            socket.close();
        }

    }

    public static void readStream(Socket tcpSocket) throws IOException, InterruptedException {
        DatagramSocket socket = getUDPSocket(tcpSocket);

        JFrame panel = new JFrame();
        panel.setAlwaysOnTop(true);
        panel.setSize(500, 500);
        JLabel label = new JLabel();
        panel.getContentPane().add(label);
        panel.setVisible(true);

        boolean recievingPackets = true;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while (recievingPackets) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            socket.receive(packet);

            String endChecker = new String(packet.getData(), 0, packet.getLength());
            if (endChecker.equals("THISISTHEENDOFTHESTREAM")) {
                recievingPackets = false;
                break;
            }


            out.write(packet.getData(), 0, packet.getLength());

            byte[] frameData = out.toByteArray();

            if (frameData.length > 0) {

                BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameData));
                if (image != null) {
                    System.out.println("Frame completely assembled");
                    ImageIcon imageIcon = new ImageIcon(image);
                    label.setIcon(imageIcon);
                    panel.repaint();
                    out.reset();
                }
            }
        }

            System.out.println("Video has finished playing");
            panel.setVisible(false);
            socket.close();
    }
    private static DatagramSocket getUDPSocket(Socket tcpSocket) throws IOException {
        DatagramSocket socket = null;
        int i;
        for (i=2001;i < 3000; i++) {
            try {
                socket = new DatagramSocket(i);
                break;
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        if (socket == null) throw new RuntimeException("Failed to create server, no available ports.");

        PrintWriter out = new PrintWriter(tcpSocket.getOutputStream(),true);
        portLock.lock();
        try {
            while (!foundPort) {
                out.println("/port " + i);
            }
            foundPort = false;
        } finally {
            portLock.unlock();
        }
        return socket;
    }

    @Override
    public void run() {

    }
}
