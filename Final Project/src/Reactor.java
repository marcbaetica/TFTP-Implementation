
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
//import java.nio.file.AccessDeniedException;
//import java.nio.file.FileAlreadyExistsException;
import java.io.IOException.*;


public class Reactor implements Runnable  {

	public static int R_NO = 1;						// Start assigning reactors from 1
	private static boolean 	EXCEPs_SILENT = true;	// Set SILENT to false if you want to see the exception stack traces
	private static boolean 	EXPLANs_SILENT = false;	// Set SILENT to false if you want to see full explanations of operations
	private boolean 	running = true;
	public static int	   	TIMEOUT = 2000;			// 2 seconds
	public static int		ResentTrials = 3;		// number of tries before reactor give up (NOT ZERO, should be 1 & above)
	
	// types of requests we can receive
	public static enum Request { READ, WRITE, DATA, ACK, ERROR};
	// MY WORK Iteration#2
	public static enum Error { UnknownError, FileNotFound, AccessViolation, DiskFull, IllegalOperation, UnknownTransID,
		UnknownTransID_DATA, UnknownTransID_ACK, FileAlreadyExists, FileNoLongerExists, IllegalOp_DATA, IllegalOp_ACK, None};
	
	// responses for valid requests
	public static final byte[] readResp = {0, 3, 0, 1};
	public static final byte[] writeResp = {0, 4, 0, 0};
	public static final byte[] invalidResp = {0, 5};
	
	private int number;		// reactor's own assigned number
	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket sendReceiveSocket;
	private BufferedOutputStream out = null;
	
	public Reactor(DatagramPacket rp){
		receivePacket = rp;
		number = R_NO;
		R_NO++;
	}
	
	public void stop(){
		System.out.println("Reactor#"+number+" -- is done processing a request & is exiting");
		running = false;
		sendReceiveSocket.close();
		if(out!=null)
			try { out.close(); } catch (IOException e) {}
	}
	
	public void run() {
			
		System.out.println("Reactor#"+number+" -- Packet received: " +
							"From host: " + receivePacket.getAddress() +
							" Host port: " + receivePacket.getPort() +
							" Length: " + receivePacket.getLength());
		byte[] data;

		// Get a reference to the data inside the received datagram.
		data = receivePacket.getData();
	
		// Form a String from the byte array.
		String received = new String(data, 0, receivePacket.getLength());
		if(!EXPLANs_SILENT)
			System.out.println("Reactor#"+number+" -- Received: "+received);
	
		try {
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(TIMEOUT);
		} catch (SocketException e1) {
			if(!EXCEPs_SILENT)
				e1.printStackTrace();
		}

		// If it's a read, send back DATA (03) block 1
		// If it's a write, send back ACK (04) block 0
		connectProfile info =  checkForError(receivePacket, null, Error.IllegalOperation);
			
		// Create a response.
		if (info.getRequest() == Request.READ) { // for Read it's 0301
			processRrq(info);
		} else if (info.getRequest() == Request.WRITE) { // for Write it's 0400
			processWrq(info);
		} else { // for invalid it's 05
			// The send back the found error (BY simply sending info)
			sendError(sendReceiveSocket, info.getAddress(), info.getPort(), info.getError(), info.getExplinantion());
		}

		sendReceiveSocket.close();
	}
	
	public void processRrq(connectProfile info){
		System.out.println("Reactor#"+number+" -- Starting the process of RRQ");

		int n = 0;
		byte[] buff = new byte[512];
		byte[] block = {0,1};
		BufferedInputStream in = null;
		File file				= new File(info.getFileName());
		if(!EXPLANs_SILENT)
			System.out.println("Reactor#"+number+" -- Trying to read file: "+info.getFileName());
		try {
			in= new BufferedInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			System.out.println("Reactor#"+number+" -- File: ("+info.getFileName()+") Is Not Found.");
			sendError(sendReceiveSocket, info.getAddress(), info.getPort(), Error.FileNotFound, 
					new String("File Not Found: "+info.getFileName()));
			return;
		} catch(Exception e){
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			sendError(sendReceiveSocket, info.getAddress(), info.getPort(), Error.UnknownError, 
					new String("Unknown Error: Reading file "+info.getFileName()));
			return;
		}
		long size = file.length();
		System.out.println("Reactor#"+number+" -- File size:"+size);
		int toSendEmptyAtTheEnd = (int) (size%512);
		try {
			while((n = in.read(buff,0,512)) != -1){
				if(!EXPLANs_SILENT)
					System.out.println("Reactor#"+number+" -- Inside the while loop (processingRRQ).");

				// Sending data
				sendData(sendReceiveSocket, info.getAddress(), info.getPort(), buff,n, block);
				
				// Receiving Ack
				int count = 0;
				if(!EXPLANs_SILENT)
				while (count < ResentTrials){
					System.out.println("Reactor#"+number+" -- Inside the ACK while loop waiting for one (processingRRQ).");
					
					byte[] data = new byte[516];
					receivePacket = new DatagramPacket(data, data.length);
					try{
						count++;
						sendReceiveSocket.receive(receivePacket);
					} catch (SocketTimeoutException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Reactor#"+number+" -- receiveACK: Expecting ACK, but it's time out sending data again. Try#"+count);
						// Resends the data again
						sendData(sendReceiveSocket, info.getAddress(), info.getPort(), buff,n, block);
						continue;			// loop again
					} catch (IOException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Reactor#"+number+" -- receiveACK: Expecting ACK, but it's I/O Exception. Is shutting down. Try#"+count);
						stop();
						return;
					}
					// check if it came from the same source
					connectProfile checking1 = checkForError(receivePacket, info, Error.UnknownTransID);
					if(errorDispatcher(sendReceiveSocket, checking1)){continue;} // Send an error if necessary, & Don't close the connection, go back to receive
					// check if client didn't send a DiskFull Error
					connectProfile checking2 = checkForError(receivePacket, info, Error.DiskFull);
					if(checking2.isError()){ stop(); return;}
					// check if client didn't send an Unknown Error
					connectProfile checking3 = checkForError(receivePacket, info, Error.UnknownError);
					if(checking3.isError()){ stop(); return;}
					// check if client didn't send an Unknown Error
					connectProfile checking4 = checkForError(receivePacket, info, Error.IllegalOp_ACK);
					if(errorDispatcher(sendReceiveSocket, checking4)){ stop(); return;}
					
					// check if it has the same expected block number
					byte[] temp = new byte[2]; temp[0] = receivePacket.getData()[2]; temp[1] = receivePacket.getData()[3];
					if(!isSameBlockNum(temp, block)) { 
						if(!EXPLANs_SILENT)
							System.out.println("Reactor#"+number+" -- receiveACK: ACK with a wrong block No., ExpectedBlock#"+
									unsignedShortToInt(block)+" ReceivedBlock#"+unsignedShortToInt(temp));
						count = 0; continue;
						}	// reset counter, and ignore the ACK
					else { 														// Otherwise, complete processing
						// Increment block
						block = increamentBlock(block);
						count = 0;
						break;
					}
				}
				
				if(count == ResentTrials){
					System.out.println("Reactor#"+number+" -- receiveACK: After all tries, reactor is giving up! Is shutting down. Try#"+count);
					stop();
					return;
				}
				
				buff = new byte[512];
			}
			
			if(toSendEmptyAtTheEnd == 0){
				System.out.println("Reactor#"+number+" -- sending an empty packet is needed");
				byte[] temp_content = new byte[0];
				sendData(sendReceiveSocket, info.getAddress(), info.getPort(), temp_content ,0, block);
				int count = 0;
				while (count < ResentTrials){
					System.out.println("Reactor#"+number+" -- Inside the ACK while loop waiting for one (processingRRQ).");
					
					byte[] data = new byte[516];
					receivePacket = new DatagramPacket(data, data.length);
					try{
						count++;
						sendReceiveSocket.receive(receivePacket);
					} catch (SocketTimeoutException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Reactor#"+number+" -- receiveACK: Expecting ACK, but it's time out sending data again. Try#"+count);
						// Resends the data again
						sendData(sendReceiveSocket, info.getAddress(), info.getPort(), temp_content ,0, block);
						continue;			// loop again
					} catch (IOException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Reactor#"+number+" -- receiveACK: Expecting ACK, but it's I/O Exception. Is shutting down. Try#"+count);
						stop();
						return;
					}
					// check if it came from the same source
					connectProfile checking1 = checkForError(receivePacket, info, Error.UnknownTransID);
					if(errorDispatcher(sendReceiveSocket, checking1)){continue;} // Send an error if necessary, & Don't close the connection, go back to receive
					// check if client didn't send a DiskFull Error
					connectProfile checking2 = checkForError(receivePacket, info, Error.DiskFull);
					if(checking2.isError()){ stop(); return;}
					// check if client didn't send an Unknown Error
					connectProfile checking3 = checkForError(receivePacket, info, Error.UnknownError);
					if(checking3.isError()){ stop(); return;}
					
					// check if it has the same expected block number
					byte[] temp = new byte[2]; temp[0] = receivePacket.getData()[2]; temp[1] = receivePacket.getData()[3];
					if(!isSameBlockNum(temp, block)) { 
						if(!EXPLANs_SILENT)
							System.out.println("Reactor#"+number+" -- receiveACK: ACK with a wrong block No., ExpectedBlock#"+
									unsignedShortToInt(block)+" ReceivedBlock#"+unsignedShortToInt(temp));
						count = 0; continue;
						}	// reset counter, and ignore the ACK
					else { 														// Otherwise, complete processing
						// Increment block
						block = increamentBlock(block);
						count = 0;
						break;
					}
				}
			}
			
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
		}
		
		System.out.println("Reactor#"+number+" -- is done processing RRQ and is exiting");
	}

	public void processWrq(connectProfile info) {

		byte[] block = {0,0};
		FileOutputStream ostream = null;
		File file				= new File(info.getFileName());
		
		if(!EXPLANs_SILENT)
			System.out.println("Reactor#"+number+" -- Trying to write file: "+info.getFileName());
		
		try{
			if(file.canWrite()==false && file.exists()){
				sendError(sendReceiveSocket, info.getAddress(), info.getPort(), Error.AccessViolation,
						new String("Error: File ("+info.getFileName()+" Is Read Only"));
				System.out.println("Reactor#"+number+" -- Client is trying to read from a read only file, sending back an Erroe");
				stop();
				return;
			} else if(file.exists()){
				throw new IOException("FileAlreadyExistsException");
			} 
			
			ostream = new FileOutputStream(file);
			out = new BufferedOutputStream(ostream);
			
		} catch(IOException e){
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			sendError(sendReceiveSocket, info.getAddress(), info.getPort(), Error.FileAlreadyExists,
					new String("Error: File ("+info.getFileName()+" Already Exists"));
			stop();
			return;
		}
		
		// Everything is OK
		sendACK(sendReceiveSocket, info.getAddress(), info.getPort(), block);
		System.out.println("Reactor#"+number+" -- send ACK with block#"+unsignedShortToInt(block));

		
		System.out.println("\nReactor#"+number+" -- About to enter the WRQ while loop\n");
		while(running){
			// receive data
			byte[] data = new byte[516];
			receivePacket = receiveData(sendReceiveSocket,receivePacket);
			
			if(receivePacket==null) { break; }
			
			data = receivePacket.getData();
			
			// Checks
			// 1) check if client send packet from different port or address
			connectProfile checking1 = checkForError(receivePacket, info, Error.UnknownTransID);
			if(errorDispatcher(sendReceiveSocket, checking1)) { continue; }	// Send an error if necessary, & Don't close the connection, go back to receive
			// 2) Not received an ERROR from client
			connectProfile checking2 = checkForError(receivePacket, info, Error.IllegalOp_DATA);
			if(errorDispatcher(sendReceiveSocket, checking2)) { stop(); return; }
			
			
			byte[] temp = new byte[2]; temp[0]=data[2]; temp[1]=data[3];
			System.out.println("Reactor#"+number+" -- Received DATA with block#"+unsignedShortToInt(temp));
			
			// 2) check if client sent the expected block#
			if(!isSameBlockNum(temp, increamentBlock(block))){		// If not the same block#
				// send an ACK with same DATA's block#
				sendACK(sendReceiveSocket, info.getAddress(), info.getPort(), temp);
				// loop again
				continue;
			}	
			
			byte[] fileToWrite = new byte[receivePacket.getLength()-4];
			
			if(!writeDATA2FileFromPacket(out, file, fileToWrite, receivePacket, receivePacket.getLength()-4)){
				stop();
				return;
			}
			
			// Creating an ACK msg
			block[0] = data[2];
			block[1] = data[3];
			
			sendACK(sendReceiveSocket, info.getAddress(), info.getPort(), block);
			
			String str_FileToWrite = new String(fileToWrite);
			
			if(!EXPLANs_SILENT){
			//	System.out.println("Reactor#"+number+" -- fileToWrite: "+str_FileToWrite);
				System.out.println("Reactor#"+number+" -- Size of fileToWrite: "+fileToWrite.length);
			}
			
			if(fileToWrite.length < 512){
				try {out.close();} catch (IOException e) {e.printStackTrace();}
				break;
			}
		}
		System.out.println("Reactor#"+number+" -- is done processing WRQ and is exiting");
	}
	
	public boolean writeDATA2FileFromPacket(BufferedOutputStream out, File file, byte[] fileToWrite, DatagramPacket p, int length ){
		fileToWrite = new byte[length];
		System.arraycopy(p.getData(), 4, fileToWrite, 0, length);
		
		if(!EXPLANs_SILENT)
			System.out.println("Reactor#"+number+" -- The server is trying to write a file of size("+fileToWrite.length+
				"b) on a disk that has a free space of ("+file.getFreeSpace()+"b).");
		
		try{
			if(file.getFreeSpace() < fileToWrite.length){
				throw new IOException("DiskIsFull");
			}
		} catch(IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			sendError(sendReceiveSocket, receivePacket.getAddress(), receivePacket.getPort(),
					Reactor.Error.DiskFull, "Error: The Disk Is Full");
			return false;
		} 
		
		// After checking if desk is full, now we write the file
		try {	
			out.write(fileToWrite,0,fileToWrite.length);
		} catch(IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			sendError(sendReceiveSocket, receivePacket.getAddress(), receivePacket.getPort(),
					Reactor.Error.UnknownError, "Error: Unknown Error");
			return false;
		} 
		
		String str_FileToWrite = new String(fileToWrite);
		
		if(fileToWrite.length < 512){
			try {out.close();} catch (IOException e) {e.printStackTrace();}
		}
		
		return true;
	}
	
	public connectProfile checkForError(DatagramPacket p, connectProfile info, Error e){
		connectProfile answer = null;
		Request typeOfReq;
		byte[] data = p.getData();
		String filename = null, mode = null;
		int len, j = 0, k = 0;
		
		switch(e){
		case UnknownError:
			if(data[0]==0 && data[1]==5 && data[2]==0 && data[3]==0){
				typeOfReq = Request.ERROR;
				answer = new connectProfile(typeOfReq, Error.UnknownError,p.getAddress(),p.getPort());
				byte[] temp = new byte[p.getLength()-4];
				System.arraycopy(p.getData(), 4, temp, 0, temp.length);
				System.out.println("Reactor#"+number+" -- Received Unknown Error with explination: "+new String(temp));
				break;
			} else if (data[1] == 4){
				typeOfReq = Request.ACK;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		case FileNotFound:
			//
			//
			break;
		case AccessViolation:
			//
			//
			break;
		case DiskFull:	// The client's disk is full
			if(data[0]==0 && data[1]==5 && data[2]==0 && data[3]==3){
				typeOfReq = Request.ERROR;
				answer = new connectProfile(typeOfReq, Error.DiskFull,p.getAddress(),p.getPort());
				byte[] temp = new byte[p.getLength()-4];
				System.arraycopy(p.getData(), 4, temp, 0, temp.length);
				System.out.println("Reactor#"+number+" -- Received DiskFull with explination: "+new String(temp));
				break;
			} else if (data[1] == 4){
				typeOfReq = Request.ACK;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		case IllegalOperation:
			if (data[0] != 0){
				typeOfReq = Request.ERROR;
				answer = new connectProfile(typeOfReq, Error.IllegalOperation, "Illegal TFTP Operation: The first byte of sent data is not 0", 
								p.getAddress(), p.getPort()); break;	}// bad 
			else if (data[1] == 1){
				typeOfReq = Request.READ; }// could be read
			else if (data[1] == 2){
				typeOfReq = Request.WRITE;}// could be write
			else{
				typeOfReq = Request.ERROR;
				answer = new connectProfile(typeOfReq, Error.UnknownError, "Unknown Error: No explination.", p.getAddress(),
										p.getPort()); break;	}// bad
	
			len = receivePacket.getLength();
	
			if (typeOfReq != Request.ERROR) { // check for filename
				// search for next all 0 byte
				for (j = 2; j < len; j++) {
					if (data[j] == 0)
						break;
				}
				if (j == len){
					typeOfReq = Request.ERROR;
					answer = new connectProfile(typeOfReq, Error.IllegalOperation,"Illegal TFTP Operation: No file name specified.",
										p.getAddress(), p.getPort()); break;	}// didn't find a 0 byte
				// otherwise, extract filename
				filename = new String(data, 2, j - 2);
			}
	
			if (typeOfReq != Request.ERROR) { // check for mode
				// search for next all 0 byte
				for (k = j + 1; j < len; k++) {
					if (data[k] == 0)
						break;
				}
				if (k == len){
					typeOfReq = Request.ERROR; // didn't find a 0 byte
					answer = new connectProfile(typeOfReq, Error.IllegalOperation,"Illegal TFTP Operation: No mode specified.", 
									p.getAddress(), p.getPort()); break;
				}
				mode = new String(data, j, k - j - 1);
			}
	
			if (k != len - 1){
				typeOfReq = Request.ERROR; // other stuff at end of packet
				answer = new connectProfile(typeOfReq, Error.IllegalOperation, "Illegal TFTP Operation: Extra data at the end.", 
									p.getAddress(), p.getPort()); break;
			}
			// Finally, after examining the packet
			answer = new connectProfile(typeOfReq,filename, mode, p.getAddress(), p.getPort());
			break;
			
		case UnknownTransID:
			if(!p.getAddress().equals(info.getAddress())){
				if(!EXPLANs_SILENT)
					System.out.println("Reactor#"+number+" -- IP Address should be: "+info.getAddress().toString() + " | but received from address: "+p.getAddress().toString());
				typeOfReq = Request.ERROR; // other stuff at end of packet
				System.out.println("Reactor#"+number+" -- Error: received a packet from a different IP Address.");
				answer = new connectProfile(typeOfReq, Error.UnknownTransID, "Unknown Transfer ID: Receieved a paket from a different IP Address.", 
									p.getAddress(), p.getPort()); break;
			} 
			else if(p.getPort() != info.getPort()){
					typeOfReq = Request.ERROR; // other stuff at end of packet
					answer = new connectProfile(typeOfReq, Error.UnknownTransID, "Unknown Transfer ID: Receieved a paket from a different Port Number.", 
										p.getAddress(), p.getPort()); break;
			}
			break;
		
		case UnknownTransID_DATA:
			if (data[0] == 0 && data[1] == 5 && data[2] == 0 && data[3] == 5){
				typeOfReq = Reactor.Request.DATA;
				System.out.println("Reactor#"+number+" -- Has received an Unknown Transfer ID Error & the other side is waiting for an DATA.");
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		
		case UnknownTransID_ACK:
			if (data[0] == 0 && data[1] == 5 && data[2] == 0 && data[3] == 5){
				System.out.println("Reactor#"+number+" -- Has received an Unknown Transfer ID Error & the other side is waiting for an ACK.");
				typeOfReq = Reactor.Request.ACK;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		
		case IllegalOp_DATA:
			if(data[0]!=0 || data[1]!=3 || p.getLength() < 4){
				typeOfReq = Request.ERROR; // other stuff at end of packet
				System.out.println("Reactor#"+number+" -- Has received unrecogunized DATA packet (Illegal TFTP Operation).");
				answer = new connectProfile(typeOfReq, Error.IllegalOperation, "Illegal TFTP Operation: Not recogunized DATA packet.", 
									p.getAddress(), p.getPort()); break;
			}
			break;

		case IllegalOp_ACK:
			if(data[0]!=0 || data[1]!=4 || p.getLength()!=4){
				typeOfReq = Request.ERROR; // other stuff at end of packet
				System.out.println("Reactor#"+number+" -- Has received unrecogunized ACK packet (Illegal TFTP Operation).");
				answer = new connectProfile(typeOfReq, Error.IllegalOperation, "Illegal TFTP Operation: Not recogunized ACK packet.", 
									p.getAddress(), p.getPort()); break;
			}
			break;
		
		case FileAlreadyExists: // The client already has the file
			//
			//
			break;	
		}
		if(answer != null && !EXPLANs_SILENT){
			switch(answer.getRequest()){
	//		case ACK: System.out.println("Reactor#"+number+" Received an ACK"); break;
	//		case DATA: System.out.println("Reactor#"+number+" Received an DATA"); break;
			default:
				System.out.println("Reactor#"+number+" Packet info: "+answer);
				break;
			}
		}
		return answer;
		
	}
	
	public void sendACK(DatagramSocket socket, InetAddress address, int port, byte[] blk){
		byte[] msg = {0,4, blk[0], blk[1]};
		try {
			socket.send(new DatagramPacket(msg, msg.length, address, port));
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		System.out.println("Reactor#"+number+" Send an ACK with Block#"+
				unsignedShortToInt(blk)+", to port: "+port+"\n");
	}
	
	public void sendData(DatagramSocket socket, InetAddress address, int port, byte[] data, int leng, byte[] block){
		byte[] msg = new byte[4+leng];
		msg[0] = 0;
		msg[1] = 3;
		System.arraycopy(block, 0, msg, 2, block.length);
		System.arraycopy(data, 0, msg, 4, leng);
		
		try {
			socket.send(new DatagramPacket(msg, msg.length, address, port));
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		System.out.println("Reactor#"+number+" -- Send DATA with block#"+
				unsignedShortToInt(block)+", to port "+port);
	}
	
	public void sendError(DatagramSocket socket, InetAddress address, int ip, Error error, String str_Explin){
		byte[] errCode = new byte[2];
		switch(error){
		case UnknownError: 		errCode[0]=0; errCode[1]=0; break;
		case FileNotFound: 		errCode[0]=0; errCode[1]=1; break;
		case AccessViolation:	errCode[0]=0; errCode[1]=2; break;
		case DiskFull:			errCode[0]=0; errCode[1]=3; break;
		case IllegalOperation: 	errCode[0]=0; errCode[1]=4; break;
		case UnknownTransID: 	errCode[0]=0; errCode[1]=5; break;
		case FileAlreadyExists:	errCode[0]=0; errCode[1]=6; break;
		}
		byte[] byte_Explin = str_Explin.getBytes();
		
		byte[] msg = new byte[4+byte_Explin.length];
		msg[0] = 0;
		msg[1] = 5;
		System.arraycopy(errCode,0,msg,2,2);
		System.arraycopy(byte_Explin, 0, msg, 4, byte_Explin.length);
		
		try {
			socket.send(new DatagramPacket(msg, msg.length, address, ip));
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		
		System.out.println("Reactor#"+number+" -- sendError: sending error to client with msg: "+new String(msg));
	/*	for(int i=0; i<msg.length; i++){
			System.out.println("byte"+i+": "+msg[i]);
		}*/
	}
	
	public DatagramPacket receiveData(DatagramSocket socket, DatagramPacket p){
		int count = 0;
		while (count < ResentTrials){
			try {
				count++;
				socket.receive(p);
			} catch (SocketTimeoutException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				if(!EXPLANs_SILENT)
					System.out.println("Reactor#"+number+" -- receiveData: Expecting data, but it's time out try again. Try#"+count);
				continue;
			} catch (IOException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				if(!EXPLANs_SILENT)
					System.out.println("Reactor#"+number+" -- receiveData: Expecting data, but it's I/O Exception. Is shutting down reactor.");
				stop(); return null;
			}
			connectProfile checking1 = checkForError(receivePacket, null, Reactor.Error.FileNotFound);
			if(errorDispatcher(sendReceiveSocket, checking1)) { stop(); return null; }
			connectProfile checking2 = checkForError(receivePacket, null, Reactor.Error.AccessViolation);
			if(errorDispatcher(sendReceiveSocket, checking2)) { stop(); return null; }
			connectProfile checking3 = checkForError(p, null, Reactor.Error.IllegalOp_DATA);
			if(errorDispatcher(socket, checking3))	{stop(); return null;} // Send an error if necessary
			
			return p;
		}
		
		if(!EXPLANs_SILENT)
			System.out.println("Reactor#"+number+" -- receiveData: Finished trying receiving data, is giving up! Now is shutting down reactor. Try#:"+count);
		return null;
	}
	
	public static byte[] increamentBlock(byte[] block){
		byte[] temp = new byte[block.length];
		for(int i = 0; i < block.length; i++){ temp[i]=block[i]; }
		
		if(temp[1]==127){
			if(temp[0]!=127){
				temp[0] = (byte) (temp[0]+1);
				temp[1] = 0;
			}else{
				temp[0] = 0;
				temp[1] = 0;
			}
		} else {
			temp[1] = (byte) (temp[1]+1);
		}
		return temp;
	}
	
	public static boolean isSameBlockNum(byte[] blk1,byte[] blk2){
		if(blk1[0] == blk2[0] && blk1[1]==blk2[1])
			return true;
		return false;
	}
	
	public boolean errorDispatcher(DatagramSocket socket, connectProfile info){
		if(socket != null && info != null && info.isError()){
			sendError(socket, info.getAddress(), info.getPort(), info.getError(), info.getExplinantion());
			return true;
		}
		return false;
	}
	
	public static final int unsignedShortToInt(byte[] b) 
	{
	    int i = 0;
	    i |= b[0] & 0xFF;
	    i <<= 8;
	    i |= b[1] & 0xFF;
	    return i;
	}
}
