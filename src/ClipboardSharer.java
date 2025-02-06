import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClipboardSharer {
    private static final String BROADCAST_ADDRESS = "255.255.255.255"; // 广播地址
    private static final int PORT = 12345;
    private static final AtomicBoolean isRemoteUpdate = new AtomicBoolean(false);
    private static String lastClipboardContent = "";

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket(PORT, InetAddress.getByName("0.0.0.0")); // 监听所有接口
            socket.setBroadcast(true); // 启用广播

            // 启动接收线程
            Thread receiverThread = new Thread(new Receiver(socket));
            receiverThread.setDaemon(true);
            receiverThread.start();

            // 启动剪切板轮询线程
            Thread clipboardPollingThread = new Thread(new ClipboardPoller(socket));
            clipboardPollingThread.setDaemon(true);
            clipboardPollingThread.start();

            System.out.println("Clipboard sharing is running. Press Ctrl+C to exit.");
            while (true) {
                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class Receiver implements Runnable {
        private final DatagramSocket socket;

        public Receiver(DatagramSocket socket) {
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

                    System.out.println("Received and updated clipboard: " + received);
                }
            } catch (IOException | IllegalStateException e) {
                System.err.println("Error in receiver thread: " + e.getMessage());
            }
        }
    }

    static class ClipboardPoller implements Runnable {
        private final DatagramSocket socket;

        public ClipboardPoller(DatagramSocket socket) {
            this.socket = socket;
        }

        private static final int MAX_UDP_PACKET_SIZE = 60000; // 保留一定空间

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                        // 限制数据包大小
                        if (data.length() > MAX_UDP_PACKET_SIZE) {
                            data = data.substring(0, MAX_UDP_PACKET_SIZE); // 仅取前60000字节
                            System.out.println("剪贴板内容过长，已截断发送...");
                        }
                        if (!data.equals(lastClipboardContent) && !isRemoteUpdate.get()) {
                            lastClipboardContent = data;
                            byte[] buffer = data.getBytes("UTF-8");
                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(BROADCAST_ADDRESS), PORT);
                            socket.send(packet);
                            System.out.println("Detected new clipboard content: " + data);
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (IOException | UnsupportedFlavorException | InterruptedException | IllegalStateException e) {
                System.err.println("Error in clipboard polling thread: " + e.getMessage());
            }
        }
    }

    // 自定义 StringSelection 类
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