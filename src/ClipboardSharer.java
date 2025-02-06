import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class ClipboardSharer {

    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int PORT = 12345;
    private static volatile boolean isRemoteUpdate = false;

    public static void main(String[] args) {
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            MulticastSocket socket = new MulticastSocket(PORT);
            socket.joinGroup(group);

            // 启动接收线程
            Thread receiverThread = new Thread(new Receiver(socket));
            receiverThread.start();

            // 监听剪切板变化
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.addFlavorListener(new ClipboardListener(socket, group));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Receiver implements Runnable {
        private MulticastSocket socket;

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
                    isRemoteUpdate = true;
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(new StringSelection(received), null);
                    isRemoteUpdate = false;
                }
            } catch (IOException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    static class ClipboardListener implements FlavorListener {
        private MulticastSocket socket;
        private InetAddress group;

        public ClipboardListener(MulticastSocket socket, InetAddress group) {
            this.socket = socket;
            this.group = group;
        }

        @Override
        public void flavorsChanged(FlavorEvent e) {
            if (!isRemoteUpdate) {
                try {
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                        byte[] buffer = data.getBytes("UTF-8");
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                        socket.send(packet);
                    }
                } catch (IOException | UnsupportedFlavorException | IllegalStateException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    // 自定义StringSelection类，用于设置剪切板内容
    static class StringSelection implements java.awt.datatransfer.Transferable {
        private String data;

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