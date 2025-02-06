import javax.sound.midi.Receiver;
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

    public static void main(String[] args) {
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            MulticastSocket socket = new MulticastSocket(PORT);
            socket.joinGroup(group);

            // 启动接收线程
            Thread receiverThread = new Thread(new Receiver(socket));
            receiverThread.setDaemon(true); // 设置为守护线程
            receiverThread.start();

            // 监听剪切板变化
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.addFlavorListener(new ClipboardListener(socket, group));

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
        private static final int MAX_RETRIES = 5;
        private static final int RETRY_DELAY_MS = 100;

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

                    // Update clipboard with retry mechanism
                    isRemoteUpdate.set(true);
                    boolean success = false;
                    for (int i = 0; i < MAX_RETRIES; i++) {
                        try {
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(new StringSelection(received), null);
                            success = true;
                            break;
                        } catch (IllegalStateException e) {
                            System.err.println("Retry " + (i + 1) + " due to clipboard access error: " + e.getMessage());
                            Thread.sleep(RETRY_DELAY_MS);
                        }
                    }
                    if (!success) {
                        System.err.println("Failed to update clipboard after " + MAX_RETRIES + " retries.");
                    }
                    isRemoteUpdate.set(false);
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error in receiver thread: " + e.getMessage());
            }
        }
    }
    static class ClipboardListener implements FlavorListener {
        private final MulticastSocket socket;
        private final InetAddress group;

        public ClipboardListener(MulticastSocket socket, InetAddress group) {
            this.socket = socket;
            this.group = group;
        }

        @Override
        public void flavorsChanged(FlavorEvent e) {
            if (!isRemoteUpdate.get()) {
                try {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                        byte[] buffer = data.getBytes("UTF-8");
                        System.out.println("Sending: " + data);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                        socket.send(packet);
                    }
                } catch (IOException | UnsupportedFlavorException | IllegalStateException ex) {
                    System.err.println("Error in clipboard listener: " + ex.getMessage());
                }
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