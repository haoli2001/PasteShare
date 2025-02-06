import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClipboardSharer {
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int PORT = 12345;
    private static final AtomicBoolean isRemoteUpdate = new AtomicBoolean(false);
    private static String lastClipboardContent = "";

    public static void main(String[] args) {
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            MulticastSocket socket = new MulticastSocket(PORT);
            socket.joinGroup(group);

            // 启动接收线程
            Thread receiverThread = new Thread(new Receiver(socket));
            receiverThread.setDaemon(true); // 设置为守护线程
            receiverThread.start();

            // 启动剪切板轮询线程
            Thread clipboardPollingThread = new Thread(new ClipboardPoller(socket, group));
            clipboardPollingThread.setDaemon(true); // 设置为守护线程
            clipboardPollingThread.start();

            // 主线程保持运行
            System.out.println("Clipboard sharing is running. Press Ctrl+C to exit.");
            while (true) {
                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class Receiver implements Runnable {
        private final MulticastSocket socket;

        public Receiver(MulticastSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024];
                while (!Thread.interrupted()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

                    // 更新剪切板
                    isRemoteUpdate.set(true);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(new StringSelection(received), null);
                    isRemoteUpdate.set(false);

                    // 打印新增的字符串
                    System.out.println("Received and updated clipboard: " + received);
                }
            } catch (IOException | IllegalStateException e) {
                System.err.println("Error in receiver thread: " + e.getMessage());
            }
        }
    }

    static class ClipboardPoller implements Runnable {
        private final MulticastSocket socket;
        private final InetAddress group;

        public ClipboardPoller(MulticastSocket socket, InetAddress group) {
            this.socket = socket;
            this.group = group;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                        if (!data.equals(lastClipboardContent) && !isRemoteUpdate.get()) {
                            lastClipboardContent = data;
                            byte[] buffer = data.getBytes("UTF-8");
                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                            socket.send(packet);

                            // 打印新增的字符串
                            System.out.println("Detected new clipboard content: " + data);
                        }
                    }
                    Thread.sleep(1000); // 每秒检查一次
                }
            } catch (IOException | UnsupportedFlavorException | InterruptedException | IllegalStateException e) {
                System.err.println("Error in clipboard polling thread: " + e.getMessage());
            }
        }
    }

    // 自定义StringSelection类，用于设置剪切板内容
    static class StringSelection implements Transferable {
        private final String data;

        public StringSelection(String data) {
            this.data = data;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.stringFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(DataFlavor.stringFlavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor))
                throw new UnsupportedFlavorException(flavor);
            return data;
        }
    }
}