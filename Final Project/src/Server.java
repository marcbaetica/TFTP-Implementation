
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Scanner;


public class Server {
	
	public static int PORT = 6900;
	public static int Thread_NO = 0;
	private int port;
	private boolean running;
	private static boolean SILENT = true; // Set SILENT to false if you want to see the exception stack traces
	
	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;
	
	
	public Server(int p) {
		port = p;
	}

	public static void main(String[] args) {
		final Server s = new Server(PORT);
		new Thread(new Runnable(){
			public void run(){
				String input = null;
				System.out.print("Please enter: [1] To Quit The Server");
		        Scanner user_input = new Scanner( System.in );
		        input = user_input.next();
		        if(input.equalsIgnoreCase("1")){
		            s.stop();
		        }
			}
		}).start();
		
		s.start();
	}

	public void stop() {
		running = false;
		receiveSocket.close();
	}

	public void start() {
		running = true;
		try {
			// Construct a datagram socket and bind it to port 69
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets.
			receiveSocket = new DatagramSocket(port);
		} catch (SocketException se) {
			if (!SILENT)
				se.printStackTrace();
			System.exit(1);
		}
		while (running) {
			// Construct a DatagramPacket for receiving packets up
	        // to 100 bytes long (the length of the byte array).
	         
			byte[] data = new byte[516];
	        receivePacket = new DatagramPacket(data, data.length);

	        System.out.println("\nServer: Waiting for packet.");
	        // Block until a datagram packet is received from receiveSocket.
	        try {
	            receiveSocket.receive(receivePacket);
	         } catch (IOException e) {
	            if(!SILENT)
	            	e.printStackTrace();
	            stop();
	            continue;
	         }
	        
			// Here we're creating a Reactor & intilizing it without any waiting
	        Thread t = new Thread (new Reactor(receivePacket));
			t.start();
		}
		
		System.out.println("Server: Is shotting down.");
	}
}
