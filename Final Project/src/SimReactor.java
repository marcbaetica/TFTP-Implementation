import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;


public class SimReactor implements Runnable{
	public static int R_NO = 1;						// Start assigning sim_reactors from 1

	private int  firstInput, secondInput, pktNum, delayTime, errInput1, errInput2, number;
	private DatagramPacket receivePacket, sendPacket;
	private DatagramSocket clientSocket, serverSocket, unkownPortSocket;
	private static boolean EXCEPs_SILENT = true; // Set SILENT to false if you want to see the exception stack traces
	private static boolean 	EXPLANs_SILENT = true;	// Set SILENT to false if you want to see full explanations of operations
	public static int	   	TIMEOUT = 6000;			// 6 seconds

	private byte[] data, sending;
	private InetAddress clientAddress, serverAddress;
	private int clientPort, serverPort, j=0, numOfLosts =0 , error=0, len, please = 0;
	private boolean gotStoped = false;
	private boolean running;
   
	private boolean cIfL=true,	// continue if lost
					unkownPort=false,
					isFirstTime=true;
	
	public SimReactor(DatagramPacket p, int firstInput_, int secondInput_, int pktNum_, int delayTime_,
						int errInput1_, int errInput2_){
		number = R_NO;
		R_NO++;
		running = true;
		receivePacket	= p;
		clientAddress	= receivePacket.getAddress();
		clientPort		= receivePacket.getPort();
		
		firstInput = firstInput_;
		secondInput = secondInput_;
		pktNum = pktNum_;
		delayTime = delayTime_;
		errInput1 = errInput1_;
		errInput2 = errInput2_;
		data = receivePacket.getData();
		
		try {
			// Construct a datagram socket and bind it to any available
	        // port on the local host machine. This socket will be used to
	        // send and receive UDP Datagram packets from the server.
			serverSocket = new DatagramSocket();
			serverSocket.setSoTimeout(TIMEOUT);
			// send and receive UDP Datagram packets from the server.
			clientSocket = new DatagramSocket();
			clientSocket.setSoTimeout(TIMEOUT);

			unkownPortSocket = new DatagramSocket();
		} catch (SocketException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
		}
		System.out.println("SimReactor#"+number+" -- Has been created. ClientPort: "+clientPort+"\n");
	}
	
	public void stop(){
		if(!gotStoped){
			System.out.println("SimReactor#"+number+" -- Is shutting down.");
			serverSocket.close();
			clientSocket.close();
		}
		gotStoped = true;
		running = false;
	}
	   
	// Edit the initial request to simulate an error
	public byte[] modifyReqPackTo(int invalid) {
		byte[] msg = new byte[100], fn  // filename 
								  ,	md; // second input
		String filename = "test.txt", mode;

		if (invalid == 1) {		// Change the opcode
			msg[0] = 1;
			msg[1] = 2;
		} else {
			msg[0] = 0;
			msg[1] = 1;
		}
		if (invalid == 4) { 	// Change the mode
			mode = "Abdoul";
		} else {
			mode = "octet";//or "netascii"
		}
		// convert to bytes
		md = mode.getBytes();
		if (invalid == 2) { 	// Corrupt size
			System.arraycopy(md, 0, msg, 3, md.length);
			len = md.length + 4; 
			
		} else {
			fn = filename.getBytes();
			System.arraycopy(fn, 0, msg, 2, fn.length);
			
			if (invalid == 3) {
				System.arraycopy(md, 0, msg, fn.length + 2, md.length);
				len = fn.length + md.length + 3; 		// set the length
			} else {
				msg[fn.length + 2] = 0;
				System.arraycopy(md, 0, msg, fn.length + 3, md.length);
				len = fn.length + md.length + 4; 		// set the length
			}
		}
		if (invalid == 5) {		// Corrupt last zero
			msg[len - 1] = 2;
		} else {										// and end with another 0 byte
			msg[len - 1] = 0;
		}
		if (invalid == 6) {		// add extra length to the end of the message
			len = len + 2;
			msg[len - 1] = 5;
			msg[len - 2] = 4;
		}
		return msg;
	}

	public void recieveFromClient() {

		data = new byte[516];// to carry larger packets (in case)
		receivePacket = new DatagramPacket(data, data.length);

		System.out.println("SimReactor#"+number+": Waiting for packet.");
		
		try {
			clientSocket.receive(receivePacket); // waiting for client packet
		} catch (SocketTimeoutException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			System.out.println("SimReactor#"+number+" -- receiveFromClient: Expecting a message from client but it's time out. Is shutting down.");
			stop();
			return;
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		
		// Display received info
		System.out.println("SimReactor#"+number+": Packet received:");
		System.out.println("From host: " + receivePacket.getAddress());
		clientPort = receivePacket.getPort();
		System.out.println("Host port: " + clientPort);
		System.out.println("Length: " + receivePacket.getLength());
		
		data = receivePacket.getData();

		// Display contents
		if(!EXPLANs_SILENT){
		System.out.println("Containing: ");
		for (j = 0; j < receivePacket.getLength(); j++) {
			System.out.println("byte " + j + " " + data[j]);
		}
		}
		System.out.println("SimReactor#"+number+": packet received from client using port "
				+ clientSocket.getLocalPort());
		System.out.println();
	}

	public void sendToServer() {
		int desired;

		if ((data[0] == 0 & data[1] == 1) || (data[0] == 0 & data[1] == 2)) { // if WRQ or RRQ
			desired = 0;
		} else {
			desired = data[3];
		}
		// Simulate TID Error (Error 5)
		if (firstInput == 1 & secondInput == 2 & pktNum == desired & numOfLosts == 0) {
			// One occurrence packet losing
			numOfLosts++;
			cIfL = false;
			sendPacket = new DatagramPacket(data, receivePacket.getLength(),
					receivePacket.getAddress(), 2125);
		} else if (firstInput == 1 & secondInput == 3 & pktNum == desired & numOfLosts == 0) {

			// delaying
			try {
				Thread.sleep(delayTime);
			} catch (InterruptedException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
			}

			// One occurrence packet losing
			numOfLosts++;
			cIfL = true;
			if(isFirstTime){
				System.out.println("SimReactor#"+number+" -- First time sending to server, to Port: "+Server.PORT);
				sendPacket = new DatagramPacket(data, len,
					receivePacket.getAddress(), Server.PORT);
			} else{
				System.out.println("SimReactor#"+number+" -- Next time (Non-First) sending to server, to Port: "+
							serverPort+", Address:"+serverAddress);
				sendPacket = new DatagramPacket(data, len,
						serverAddress, serverPort);
			}

		} else { // When Error 4 is requested
			len = receivePacket.getLength();
			if (firstInput == 1 & secondInput == 5 & pktNum == 1 & numOfLosts == 0
					& (errInput1 == 1 || errInput1 == 2)) {

				if (errInput2 == 1) {
					modifyReqPackTo(1);
				} else if (errInput2 == 2) {
					modifyReqPackTo(2);
				} else if (errInput2 == 3) {
					modifyReqPackTo(3);
				} else if (errInput2 == 4) {
					modifyReqPackTo(4);
				} else if (errInput2 == 5) {
					modifyReqPackTo(5);
				} else if (errInput2 == 6) {
					modifyReqPackTo(6);
				}
				data = modifyReqPackTo(errInput2);
			}
			if (firstInput == 1 & secondInput == 5 & pktNum == desired & numOfLosts == 0
					& (errInput1 == 3 || errInput1 == 4)) {
				len = receivePacket.getLength();
				if (errInput2 == 1) {
					data[0] = 9;
					data[1] = 9;
				} else if (errInput2 == 2) {
					len = 3;
				} else if (errInput2 == 3) {

					if (errInput1 == 3) {
						data[516] = 7;
						data[517] = 7;
						len = 518;
					} else {
						data[4] = 7;
						data[5] = 7;
						len = 6;
					}

				} else if (errInput2 == 4) {
					data[2] = 9;
					data[3] = 9;
				}

			}
			
			if(isFirstTime){
				System.out.println("SimReactor#"+number+": First time sending packet to server using port "+
						serverSocket.getLocalPort()+", to serverPort: "+Server.PORT);

				sendPacket = new DatagramPacket(data, len,
					receivePacket.getAddress(), Server.PORT);
			} else{
				System.out.println("SimReactor#"+number+": Next time (Non-First) sending packet to server using port "+
						serverSocket.getLocalPort()+", to serverPort: "+serverPort);
				sendPacket = new DatagramPacket(data, len,
						serverAddress, serverPort);
			}
			cIfL = true;
		}
		// Display received info
		System.out.println("SimReactor#"+number+": sending packet.");
		System.out.println("To host: " + sendPacket.getAddress());
		System.out.println("Destination host port: " + sendPacket.getPort());
		System.out.println("Length: " + sendPacket.getLength());
		// Display contents
		if(!EXPLANs_SILENT){
		System.out.println("Containing: ");
		data = sendPacket.getData();
		for (j = 0; j < sendPacket.getLength(); j++) {
			System.out.println("byte " + j + " " + data[j]);
		}
		}
		System.out.println("SimReactor#"+number+": packet sent to server using port "+serverSocket.getLocalPort());
		System.out.println();
		try {
			// sendReceiveSocket.setSoTimeout(2000);
			serverSocket.send(sendPacket);

		} catch (SocketTimeoutException ex) {
			error++;
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		if (firstInput == 1 & secondInput == 4 & pktNum == desired) {
			try {
				// sendReceiveSocket.setSoTimeout(2000);
				Thread.sleep(delayTime);
				serverSocket.send(sendPacket);

			} catch (SocketTimeoutException ex) {
				error++;
			} catch (IOException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				stop();
				return;
			} catch (InterruptedException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				stop();
				return;
			}
		}

		if (firstInput == 1 & secondInput == 6 & pktNum == desired) {
			unkownPort = true;
			try {
				// sendReceiveSocket.setSoTimeout(2000);
				unkownPortSocket.send(sendPacket);

			} catch (IOException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				stop();
				return;
			}
		}

	}

	public void recieveUnkown() throws SocketException {

		unkownPortSocket.setSoTimeout(1000);
		System.out.println("SimReactor#"+number+": recieveUnkown.");
		System.out.println("SimReactor#"+number+": Waiting for Error packet.");
		try {
			unkownPortSocket.receive(receivePacket);	// waiting for packet
		} catch (SocketTimeoutException ex) {

		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}

		// Display received info
		System.out.println("SimReactor#"+number+": Packet received:");
		System.out.println("From host: " + receivePacket.getAddress());
		System.out.println("Host port: " + receivePacket.getPort());
		System.out.println("Length: " + receivePacket.getLength());
		data = receivePacket.getData();
		// Display contents
		if(!EXPLANs_SILENT){
			System.out.println("Containing: ");
			for (j = 0; j < receivePacket.getLength(); j++) {
				System.out.println("byte " + j + " " + data[j]);
			}
		}

		unkownPortSocket.close();
	}

	public void recieveFromServer() {

		data = new byte[516];
		receivePacket = new DatagramPacket(data, data.length);

		System.out.println("SimReactor#"+number+": Waiting for packet.");
		try {
			serverSocket.receive(receivePacket); // waiting for client packet
			
		} catch (SocketTimeoutException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			System.out.println("SimReactor#"+number+" -- receiveFromServer: Expecting a message from server but it's time out. Is shutting down.");
			stop();
			return;
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		
		if(isFirstTime){
			serverAddress = receivePacket.getAddress();
			serverPort = receivePacket.getPort();
			System.out.println("SimReactor#"+number+": First time receiving from server on port "+serverSocket.getLocalPort()+
						". ServerPort: "+serverPort+", Address:"+serverAddress);
			isFirstTime = false;
		}
		else
			System.out.println("SimReactor#"+number+": Next time (Non-First) receiving from server on port "
					+serverSocket.getLocalPort()+ ". ServerPort: "+ serverPort+", Address:"+serverAddress);


		// Display info
		System.out.println("SimReactor#"+number+": Packet received:");
		System.out.println("From host: " + receivePacket.getAddress());
		System.out.println("Host port: " + receivePacket.getPort());
		System.out.println("Length: " + receivePacket.getLength());

		data = receivePacket.getData();
		// Display contents
		if(!EXPLANs_SILENT){
		System.out.println("Containing: ");
		for (j = 0; j < receivePacket.getLength(); j++) {
			System.out.println("byte " + j + " " + data[j]);
		}
		}
		System.out.println("SimReactor#"+number+": packet received from server using port "
				+ serverSocket.getLocalPort());
		System.out.println();
	}

	public void sendToClient() {
		int desired;

		if ((data[0] == 0 & data[1] == 1) || (data[0] == 0 & data[1] == 2)) {
			desired = 0;
		} else {
			desired = data[3];
		}
		
		if (firstInput == 2 & secondInput == 2 & pktNum == desired & numOfLosts == 0) { // To lose the packet, send to random port
			numOfLosts++;
			cIfL = false;
			sendPacket = new DatagramPacket(data, receivePacket.getLength(),
					clientAddress, 3459);
		} else if (firstInput == 2 & secondInput == 3 & pktNum == desired & numOfLosts == 0) {

			// delaying
			try {
				Thread.sleep(delayTime);
			} catch (InterruptedException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
			}
			// One occurrence packet losing
			numOfLosts++;
			cIfL = true;
			sendPacket = new DatagramPacket(data, receivePacket.getLength(),
					clientAddress, clientPort);
		} else {
			len = receivePacket.getLength();
			if (firstInput == 2 & secondInput == 5 & pktNum == desired & numOfLosts == 0
					& (errInput1 == 3 || errInput1 == 4)) {

				if (errInput2 == 1) {
					data[0] = 9;
					data[1] = 9;
				} else if (errInput2 == 2) {
					len = 3;
				} else if (errInput2 == 3) {

					if (errInput1 == 3) {
						data[516] = 7;
						data[517] = 7;
						len = 518;
					} else {
						data[4] = 7;
						data[5] = 7;
						len = 6;
					}

				} else if (errInput2 == 4) {
					data[2] = 9;
					data[3] = 9;
				}

			}
			sendPacket = new DatagramPacket(data, len, clientAddress, clientPort);
			cIfL = true;
		}
		System.out.println("SimReactor#"+number+": Sending packet:");
		System.out.println("To host: " + sendPacket.getAddress());
		System.out.println("Destination host port: " + sendPacket.getPort());
		System.out.println("Length: " + sendPacket.getLength());
		sending = sendPacket.getData();
		if(!EXPLANs_SILENT){
		System.out.println("Containing: ");
		for (j = 0; j < sendPacket.getLength(); j++) {
			System.out.println("byte " + j + " " + sending[j]);
		}
		}

		try {
			clientSocket.send(sendPacket);	// send to client
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		if (firstInput == 2 & secondInput == 4 & pktNum == desired) {
			try {
				// sendReceiveSocket.setSoTimeout(2000);
				Thread.sleep(delayTime);
				clientSocket.send(sendPacket);

			} catch (SocketTimeoutException ex) {
				error++;
			} catch (IOException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				stop();
				return;
			} catch (InterruptedException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				stop();
				return;
			}
		}

		System.out.println("SimReactor#"+number+": packet sent to client using port "
				+ clientSocket.getLocalPort());
		System.out.println();

	}
	
	public static final int unsignedShortToInt(byte[] b) 
	{
	    int i = 0;
	    i |= b[0] & 0xFF;
	    i <<= 8;
	    i |= b[1] & 0xFF;
	    return i;
	}
	

	@Override
	public void run() {
		while(running) { // loop forever
	    	 if (please == 1)unkownPort=false;
	    	 
	         sendToServer();
	         if(!running) return;
	         // Send the datagram packet to the server via the send/receive socket.
	         if (cIfL){
	        	 recieveFromServer();
	        	 if(!running) { System.out.println("------------¥------------¥------------¥------------\n"); return;}
	        	   sendToClient();
	        	      if(unkownPort){
	        	          try {
							recieveUnkown();
						} catch (SocketException e) {
							e.printStackTrace();
						}
						 	please=1;
	        	      }
	         }
	         recieveFromClient();
	         
	         System.out.println("------------¥------------¥------------¥------------\n");
	      } 
	}

}
