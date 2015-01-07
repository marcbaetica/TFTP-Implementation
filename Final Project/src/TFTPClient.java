
//TFTPClient.java
//This class is the client side for a very simple assignment based on TFTP on
//UDP/IP. The client uses one port and sends a read or write request and gets 
//the appropriate response from the server.  No actual file transfer takes place.  

import java.io.*;
import java.net.*;
import java.util.Scanner;


public class TFTPClient {

	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket sendReceiveSocket;
	public  int sendPort;
	private int number;
	public static int CLIENT_NO = 1;
	private boolean running;
	private static boolean EXCEPs_SILENT = true; // Set SILENT to false if you want to see the exception stack traces
	private static boolean EXPLANs_SILENT = false;	// Set SILENT to false if you want to see full explanations of operations
	public static int	   	TIMEOUT = 2000;			// 2 seconds
	public static int		ResentTrials = 3;		// number of tries before reactor give up
	
	// we can run in normal (send directly to server) or test
	// (send to simulator) mode
	public static enum Mode {NORMAL, TEST};

	public void stop(){
		System.out.println("Client#"+number+" -- Is shutting down.");
		running = false;
		sendReceiveSocket.close();
	}
	
	public TFTPClient() {
		number = CLIENT_NO++;
		Mode run = Mode.TEST; // change to NORMAL to send directly to server
		if (run == Mode.NORMAL)
			sendPort = Server.PORT;
		else
			sendPort = Simulator.PORT;
		try {
			// Construct a datagram socket and bind it to any available
			// port on the local host machine. This socket will be used to
			// send and receive UDP Datagram packets.
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(TIMEOUT);
			running = true;
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
		}
	}

	public connectProfile checkForError(DatagramPacket p, connectProfile info, Reactor.Error e){
		connectProfile answer = null;
		Reactor.Request typeOfReq;
		byte[] data = p.getData();
		String filename = null, mode = null;
		int len, j = 0, k = 0;
		
		switch(e){
		case UnknownError:
			if(data[0]==0 && data[1]==5 && data[2]==0 && data[3]==0){
				typeOfReq = Reactor.Request.ERROR;
				answer = new connectProfile(typeOfReq, Reactor.Error.UnknownError,p.getAddress(),p.getPort());
				byte[] temp = new byte[p.getLength()-4];
				System.arraycopy(p.getData(), 4, temp, 0, temp.length);
				System.out.println("Client#"+number+" -- Received Unknown Error with explination: "+new String(temp));
				break;
			} else if (data[1] == 4){
				typeOfReq = Reactor.Request.ACK;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		case FileNotFound:	// server didn't find the file
			if(data[0]==0 && data[1]==5 && data[2]==0 && data[3]==1){
				typeOfReq = Reactor.Request.ERROR;
				answer = new connectProfile(typeOfReq, Reactor.Error.FileNotFound,p.getAddress(),p.getPort());
				break;
			} else if (data[1] == 3){
				typeOfReq = Reactor.Request.DATA;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			} else if (data[1] == 4){
				typeOfReq = Reactor.Request.ACK;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		case AccessViolation:
			if(data[0]==0 && data[1]==5 && data[2]==0 && data[3]==2){
				typeOfReq = Reactor.Request.ERROR;
				answer = new connectProfile(typeOfReq, Reactor.Error.AccessViolation,p.getAddress(),p.getPort());
				break;
			} else if (data[1] == 3){
				typeOfReq = Reactor.Request.DATA;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			} 
			break;
		case DiskFull:	// The server's disk is full
			if(data[0]==0 && data[1]==5 && data[2]==0 && data[3]==3){
				typeOfReq = Reactor.Request.ERROR;
				answer = new connectProfile(typeOfReq, Reactor.Error.DiskFull,p.getAddress(),p.getPort());
				byte[] temp = new byte[p.getLength()-4];
				System.arraycopy(p.getData(), 4, temp, 0, temp.length);
				System.out.println("Client#"+number+" -- Received DiskFull with explination: "+new String(temp));
				break;
			} else if (data[1] == 4){
				typeOfReq = Reactor.Request.ACK;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		case IllegalOperation:
			if (data[0] != 0){
				typeOfReq = Reactor.Request.ERROR;
				answer = new connectProfile(typeOfReq, Reactor.Error.IllegalOperation, "Illegal TFTP Operation: The first byte of sent data is not 0", 
								p.getAddress(), p.getPort()); break;	}// bad 
			else if (data[1] == 1){
				typeOfReq = Reactor.Request.READ; }// could be read
			else if (data[1] == 2){
				typeOfReq = Reactor.Request.WRITE;}// could be write
			else{
				typeOfReq = Reactor.Request.ERROR;
				answer = new connectProfile(typeOfReq, Reactor.Error.UnknownError, "Unknown Error: No explination.", p.getAddress(),
										p.getPort()); break;	}// bad
	
			len = receivePacket.getLength();
	
			if (typeOfReq != Reactor.Request.ERROR) { // check for filename
				// search for next all 0 byte
				for (j = 2; j < len; j++) {
					if (data[j] == 0)
						break;
				}
				if (j == len){
					typeOfReq = Reactor.Request.ERROR;
					answer = new connectProfile(typeOfReq, Reactor.Error.IllegalOperation,"Illegal TFTP Operation: No file name specified.",
										p.getAddress(), p.getPort()); break;	}// didn't find a 0 byte
				// otherwise, extract filename
				filename = new String(data, 2, j - 2);
			}
	
			if (typeOfReq != Reactor.Request.ERROR) { // check for mode
				// search for next all 0 byte
				for (k = j + 1; j < len; k++) {
					if (data[k] == 0)
						break;
				}
				if (k == len){
					typeOfReq = Reactor.Request.ERROR; // didn't find a 0 byte
					answer = new connectProfile(typeOfReq, Reactor.Error.IllegalOperation,"Illegal TFTP Operation: No mode specified.", 
									p.getAddress(), p.getPort()); break;
				}
				mode = new String(data, j, k - j - 1);
			}
	
			if (k != len - 1){
				typeOfReq = Reactor.Request.ERROR; // other stuff at end of packet
				answer = new connectProfile(typeOfReq, Reactor.Error.IllegalOperation, "Illegal TFTP Operation: Extra data at the end.", 
									p.getAddress(), p.getPort()); break;
			}
			// Finally, after examining the packet
			answer = new connectProfile(typeOfReq,filename, mode, p.getAddress(), p.getPort());
			break;
			
		case FileAlreadyExists: // The client already has the file
			if(data[0]==0 && data[1]==5 && data[2]==0 && data[3]==6){
				typeOfReq = Reactor.Request.ERROR;
				answer = new connectProfile(typeOfReq, Reactor.Error.FileAlreadyExists,p.getAddress(),p.getPort());
				break;
			}
			break;	
			
		case UnknownTransID:
			 if(!p.getAddress().equals(info.getAddress())){
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- IP Address should be: "+info.getAddress().toString() + " | but received from address: "+p.getAddress().toString());
				typeOfReq = Reactor.Request.ERROR;
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- Error: received a packet from a different IP Address.");
				answer = new connectProfile(typeOfReq, Reactor.Error.UnknownTransID, "Unknown Transfer ID: Receieved a paket from a different IP Address.", 
									p.getAddress(), p.getPort()); break;
			} else if(p.getPort() != info.getPort()){
					typeOfReq = Reactor.Request.ERROR; // other stuff at end of packet
					answer = new connectProfile(typeOfReq, Reactor.Error.UnknownTransID, "Unknown Transfer ID: Receieved a paket from a different Port Number.", 
										p.getAddress(), p.getPort()); break;
			}
			break;
			
		case UnknownTransID_DATA:
			if (data[0] == 0 && data[1] == 5 && data[2] == 0 && data[3] == 5){
				typeOfReq = Reactor.Request.DATA;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		
		case UnknownTransID_ACK:
			if (data[0] == 0 && data[1] == 5 && data[2] == 0 && data[3] == 5){
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- Has received an Unknown Transfer ID Error & the other side is waiting for an ACK.");
				typeOfReq = Reactor.Request.ACK;
				answer = new connectProfile(typeOfReq,p.getAddress(),p.getPort());
			}
			break;
		
		case IllegalOp_DATA:
			if(data[0]!=0 || data[1]!=3 || p.getLength() < 4){
				typeOfReq = Reactor.Request.ERROR; // other stuff at end of packet
				answer = new connectProfile(typeOfReq, Reactor.Error.IllegalOperation, "Illegal TFTP Operation: Not regunized DATA packet.", 
									p.getAddress(), p.getPort()); break;
			}
			break;

		case IllegalOp_ACK:
			if(p.getData()[0]!=0 || p.getData()[1]!=4 || p.getLength()!=4){
				typeOfReq = Reactor.Request.ERROR; // other stuff at end of packet
				answer = new connectProfile(typeOfReq, Reactor.Error.IllegalOperation, "Illegal TFTP Operation: Not regunized ACK packet.", 
									p.getAddress(), p.getPort()); break;
			}
			break;
		}
	/*	if(answer != null && !EXPLANs_SILENT){
			switch(answer.getRequest()){
	//		case ACK: System.out.println("Client#"+number+" Received an ACK & block #" + unsignedShortToInt(p.getData()[2],p.getData()[3])); break;
			case DATA: System.out.println("Client#"+number+" Received a DATA and block #" + unsignedShortToInt(p.getData()[2],p.getData()[3]) ); break;
			case ERROR: System.out.println("Client#"+number+" Received an ERROR ("+answer.getError() +") with explination: "+answer.getExplinantion()); break;
			default:
				System.out.println("Client#"+number+" Packet info: "+answer);
				break;
			}
		}*/
		return answer;		
	}

	public void sendRRQ(String destination, String requested_FileName, String mode, InetAddress address, int port){
		byte[] block = new byte[2];
		BufferedOutputStream out 	= null;
		FileOutputStream ostream 	= null;
		File file					= new File(destination);
		byte[] fileToWrite 			= null;
		byte[] data;
		boolean isWritable			= true;
		
		try {
			ostream = new FileOutputStream(file);
		} catch (FileNotFoundException e1) {
			if(!EXCEPs_SILENT)
				e1.printStackTrace();
		}
		out = new BufferedOutputStream(ostream);
		
		receivePacket = null;
		int count = 0;
		
		// send a request, and till an ACK is received, otherwise resend the request again
		while(receivePacket == null && running && count < ResentTrials){
			data = new byte[516];
			receivePacket = new DatagramPacket(data, data.length);
			// send a RRQ
			count++;
			sendARequest(sendReceiveSocket, address, port, Reactor.Request.READ,requested_FileName, mode);
			System.out.println("Client#"+number+" -- sent RRQ number#"+count+".");

			// receive DATA from server
			receivePacket = new DatagramPacket(data, data.length);
			receivePacket = receiveData(sendReceiveSocket,receivePacket);
			if(receivePacket!=null){
				// send an ACK first
				byte[] temp = new byte[2];	temp[0]=receivePacket.getData()[2];
											temp[1]=receivePacket.getData()[3];
											
				if(temp[0]==0 && temp[1]==1){ // if received blk#1 & is writable
					if(!writeDATA2FileFromPacket( out, file, fileToWrite, receivePacket,  receivePacket.getLength()-4 )){
						stop();
						return;
					}
					sendACK(sendReceiveSocket, receivePacket.getAddress(), receivePacket.getPort(), temp);
					break;
				}
				else
					receivePacket = null;
			}
		}
		if(receivePacket == null || !running){
			System.out.println("Client#"+number+" -- Due to unexpected network issues, client is done RRQ and is shutting down.");
			return;
		}
		count = 0;
		
		data = receivePacket.getData();

		// create serverInfo to keep track of its IP Address & Port number
		connectProfile serverInfo = new connectProfile(Reactor.Request.READ,requested_FileName, mode, receivePacket.getAddress(), receivePacket.getPort());
		/////////////////////////////// serverInfo IS READY ///////////////////////////////
		
		
		block[0] = receivePacket.getData()[2];
		block[1] = receivePacket.getData()[3];
		
		if(receivePacket.getLength() < 516){
			System.out.println("Client#"+number+" -- First DATA Packet has been received and it's the only one.");
			try {
				out.close();
			} catch (IOException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
			}
			stop();
		}
		
		while(running){
			if(!EXPLANs_SILENT)
				System.out.println("Client#"+number+" -- inside RRQ while loop");
			// receive data
			data = new byte[516];
			receivePacket = receiveData(sendReceiveSocket,receivePacket);
			
			if(receivePacket==null) { break; }
			
			data = receivePacket.getData();
			
			// Checks
			// 1) check if client send packet from different port or address
			connectProfile checking1 = checkForError(receivePacket, serverInfo, Reactor.Error.UnknownTransID);
			if(errorDispatcher(sendReceiveSocket, checking1)) { continue; }	// Send an error if necessary, & Don't close the connection, go back to receive
			// 2) check if client sent the expected block#
			byte[] temp = new byte[2]; temp[0]=data[2]; temp[1]=data[3];
			if(!isSameBlockNum(temp, increamentBlock(block))){		// If not the same block#
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- received a DATA with a wrong block No., ExpectedBlock#"+
							unsignedShortToInt(increamentBlock(block))+"("+block[1]+") ReceivedBlock#"+unsignedShortToInt(temp)+"("+temp[1]+"). Now resending another ACK with the same ReceivedBlock and wait again.");
				// send an ACK with same DATA's block#
				sendACK(sendReceiveSocket, serverInfo.getAddress(), serverInfo.getPort(), temp);
				// loop again
				continue;
			}	
			
			if(!writeDATA2FileFromPacket(out, file, fileToWrite, receivePacket, receivePacket.getLength()-4)){
				stop();
				return;
			}
			
			// Creating an ACK msg
			block[0] = data[2];
			block[1] = data[3];
			
			sendACK(sendReceiveSocket, serverInfo.getAddress(), serverInfo.getPort(), block);
			
			if(receivePacket.getLength()-4 < 512)
				break;
		}
		System.out.println("Client#"+number+" -- is done processing sending RRQ and is exiting");
	}

	public boolean writeDATA2FileFromPacket(BufferedOutputStream out, File file, byte[] fileToWrite, DatagramPacket p, int length ){
		fileToWrite = new byte[length];
		System.arraycopy(p.getData(), 4, fileToWrite, 0, length);
		
		if(!EXPLANs_SILENT)
			System.out.println("Client#"+number+" -- The client is trying to write a file of size("+fileToWrite.length+
				"b) on a disk that has a free space of ("+file.getFreeSpace()+"b).");
		
		try{
			if(file.getFreeSpace() < fileToWrite.length){
				System.out.println("Client#"+number+" -- throwed an exception because of no space");
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
	
	public void sendWRQ(String source, String fileToBeWritten,String mode, InetAddress address, int port){
		int n = 0;
		byte[] buff = new byte[512];
		byte[] block = {0,1};
		BufferedInputStream in = null;
		File file			   = new File(source);

		byte[] data;
		connectProfile serverInfo = new connectProfile(Reactor.Request.WRITE,fileToBeWritten, mode, address, port);
		
		receivePacket = null;
		int count = 0;
		
		// send a request, and till an ACK is received, otherwise resend the request again
		while(receivePacket == null && running && count < ResentTrials){
			data = new byte[516];
			receivePacket = new DatagramPacket(data, data.length);
			// send a WRQ
			count++;
			sendARequest(sendReceiveSocket, address, port, Reactor.Request.WRITE,fileToBeWritten, mode);
			System.out.println("Client#"+number+" -- sent WRQ number#"+count+".");

			// receive an ACK from server
			receivePacket = receiveACK(sendReceiveSocket, receivePacket);
			if(receivePacket!=null && (receivePacket.getData()[2]==0 && receivePacket.getData()[3]==0))
				break;
			else
				receivePacket = null;
		}
		if(receivePacket == null || !running){
			System.out.println("Client#"+number+" -- Due to unexpected network issues, client is done WRQ and is shutting down.");
			return;
		}
		System.out.println("Client#"+number+" Received an ACK with block #" + unsignedShortToInt(receivePacket.getData()[2],receivePacket.getData()[3])+"\n");
		count = 0;
		data = receivePacket.getData();
		
		
		// Update the address and the port of the server
		serverInfo.setAddress(receivePacket.getAddress());
		serverInfo.setPort(receivePacket.getPort());
		
		try {
			in = new BufferedInputStream(new FileInputStream(source));
		} catch (FileNotFoundException e1) {
			if(!EXCEPs_SILENT)
				e1.printStackTrace();
			stop();
			return;
		}
		long size = file.length();
		System.out.println("client"+number+" -- File size:"+size);
		int toSendEmptyAtTheEnd = (int) (size%512);
		try {
			while((n = in.read(buff,0,512)) != -1){
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- Inside while loop (sendWRQ)");
				// Sending data
				sendData(sendReceiveSocket, serverInfo.getAddress(), serverInfo.getPort(), buff,n, block);
				
				// Receiving Ack
				count = 0;
				while (count < ResentTrials){
					data = new byte[516];
					receivePacket = new DatagramPacket(data, data.length);
					try{
						count++;
						sendReceiveSocket.receive(receivePacket);
					} catch (SocketTimeoutException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Client#"+number+" -- receiveACK: Expecting ACK, but it's time out sending data again. Try#"+count);
						// Resends the data again
						sendData(sendReceiveSocket, serverInfo.getAddress(), serverInfo.getPort(), buff,n, block);
						continue;			// loop again
					} catch (IOException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Client#"+number+" -- receiveACK: Expecting ACK, but it's I/O Exception. Is shutting down. Try#"+count);
						stop();
						return;
					}
					
					// check if it came from the same source
					connectProfile checking1 = checkForError(receivePacket, serverInfo, Reactor.Error.UnknownTransID);
					if(errorDispatcher(sendReceiveSocket, checking1)){continue;} // Send an error if necessary, & Don't close the connection, go back to receive
					// check if client didn't send a DiskFull Error
					connectProfile checking2 = checkForError(receivePacket, serverInfo, Reactor.Error.DiskFull);
					if(checking2.isError()){ stop(); return;}
					// check if client didn't send an Unknown Error
					connectProfile checking3 = checkForError(receivePacket, serverInfo, Reactor.Error.UnknownError);
					if(checking3.isError()){ stop(); return;}
					// check if received an ACK
					connectProfile checking4 = checkForError(receivePacket, serverInfo, Reactor.Error.IllegalOp_ACK);
					if(errorDispatcher(sendReceiveSocket, checking4)){ stop(); return;}
					
					// check if it has the same expected block number
					byte[] temp = new byte[2]; temp[0] = receivePacket.getData()[2]; temp[1] = receivePacket.getData()[3];
					if(!isSameBlockNum(temp, block)) {
						System.out.println("Client#"+number+" -- receiveACK: Received an ACK, yet with a different block number. ReceivedBlock#"+
								unsignedShortToInt(temp)+", ExpectedBlock#"+unsignedShortToInt(block));
						count = 0; continue;
					}	// reset counter, and ignore the ACK
					else { 													// Otherwise, complete processing
						System.out.println("Client#"+number+" Received an ACK with block #" + unsignedShortToInt(temp)+"\n");
						// Increment block
						block = increamentBlock(block);
						count = 0;
						break;
					}
				}
				
				if(count == ResentTrials){
					System.out.println("Client#"+number+" -- receiveACK: After all tries, reactor is giving up! Is shutting down. Try#"+count);
					stop();
					return;
				}
				
				buff = new byte[512];
			}
			if(toSendEmptyAtTheEnd == 0){
				System.out.println("Client#"+number+" -- sending an empty packet is needed");
				byte[] temp_content = new byte[0];
				sendData(sendReceiveSocket, serverInfo.getAddress(), serverInfo.getPort(), temp_content ,0, block);
				int count2 = 0;
				while (count < ResentTrials){
					System.out.println("Client#"+number+" -- Inside the ACK while loop waiting for one (processingRRQ).");
					
					data = new byte[516];
					receivePacket = new DatagramPacket(data, data.length);
					try{
						count2++;
						sendReceiveSocket.receive(receivePacket);
					} catch (SocketTimeoutException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Client#"+number+" -- receiveACK: Expecting ACK, but it's time out sending data again. Try#"+count);
						// Resends the data again
						sendData(sendReceiveSocket, serverInfo.getAddress(), serverInfo.getPort(), temp_content ,0, block);
						continue;			// loop again
					} catch (IOException e){
						if(!EXCEPs_SILENT)
							e.printStackTrace();
						if(!EXPLANs_SILENT)
							System.out.println("Client#"+number+" -- receiveACK: Expecting ACK, but it's I/O Exception. Is shutting down. Try#"+count);
						stop();
						return;
					}
					// check if it came from the same source
					connectProfile checking1 = checkForError(receivePacket, serverInfo, Reactor.Error.UnknownTransID);
					if(errorDispatcher(sendReceiveSocket, checking1)){continue;} // Send an error if necessary, & Don't close the connection, go back to receive
					// check if client didn't send a DiskFull Error
					connectProfile checking2 = checkForError(receivePacket, serverInfo, Reactor.Error.DiskFull);
					if(checking2.isError()){ stop(); return;}
					// check if client didn't send an Unknown Error
					connectProfile checking3 = checkForError(receivePacket, serverInfo, Reactor.Error.UnknownError);
					if(checking3.isError()){ stop(); return;}
					
					// check if it has the same expected block number
					byte[] temp = new byte[2]; temp[0] = receivePacket.getData()[2]; temp[1] = receivePacket.getData()[3];
					if(!isSameBlockNum(temp, block)) { 
						if(!EXPLANs_SILENT)
							System.out.println("Client#"+number+" -- receiveACK: ACK with a wrong block No., ExpectedBlock#"+
									unsignedShortToInt(block)+" ReceivedBlock#"+unsignedShortToInt(temp));
						count2 = 0; continue;
						}	// reset counter, and ignore the ACK
					else { 														// Otherwise, complete processing
						// Increment block
						block = increamentBlock(block);
						count2 = 0;
						break;
					}
				}
			}
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		
		
		System.out.println("Client#"+number+" -- Is done WRQ succesfully and is shutting down.");
		
	}
	
	public DatagramPacket receiveACK(DatagramSocket socket, DatagramPacket p){
		int count = 0;
		while(count < ResentTrials){
			try {
				count++;
				sendReceiveSocket.receive(receivePacket);
			} catch (SocketTimeoutException e) {
				if (!EXCEPs_SILENT)
					e.printStackTrace();
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- receiveACK: Expecting ACK, but it's time out try again. Try#"+count);
				continue;
			} catch (IOException e) {
				if (!EXCEPs_SILENT)
					e.printStackTrace();
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- receiveACK: Expecting ACK, but it's I/O Exception. Is shutting down reactor.");
				stop();
				return null;
			}
			
			connectProfile checking1 = this.checkForError(p, null, Reactor.Error.AccessViolation);
			if(errorDispatcher(sendReceiveSocket, checking1)) { stop(); return null;}
			
			connectProfile checking2 = this.checkForError(p, null, Reactor.Error.FileAlreadyExists);
			if(errorDispatcher(sendReceiveSocket, checking2)) { stop(); return null;}
			
			connectProfile checking3 = checkForError(p, null, Reactor.Error.IllegalOp_ACK);
			if(errorDispatcher(socket, checking3))	{stop(); return null;} // Send an error if necessary
			
			connectProfile checking4 = checkForError(p, null, Reactor.Error.UnknownError);
			if(checking4.isError()){ stop(); return null;}
			
			return p;
		}
		
		if(!EXPLANs_SILENT)
			System.out.println("Client#"+number+" -- receiveACK: Finished trying receiving ACK, is giving up!. Try#:"+count);
		//stop();
		return null;
	}
	
	public void sendARequest(DatagramSocket socket, InetAddress address, int ip, Reactor.Request req, String fileName, String mode){
		int currentLeng = 0;
		byte[] msg = new byte[4+fileName.getBytes().length+mode.getBytes().length];
		if(req == Reactor.Request.READ){
			msg[0] = 0;
			msg[1] = 1;
		}
		else if (req == Reactor.Request.WRITE){
			msg[0] = 0;
			msg[1] = 2;
		}
		currentLeng = 2;
		System.arraycopy(fileName.getBytes(), 0, msg, currentLeng, fileName.getBytes().length);
		currentLeng = currentLeng + fileName.getBytes().length;
		msg[currentLeng] = 0;
		currentLeng++;
		System.arraycopy(mode.getBytes(), 0, msg, currentLeng, mode.getBytes().length);
		currentLeng = currentLeng + mode.getBytes().length;
		msg[currentLeng] = 0;
		try {
			socket.send(new DatagramPacket(msg, msg.length, address, ip));
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
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
		System.out.println("Client#"+number+" -- send ACK with Block#"+
				unsignedShortToInt(blk)+", to port "+port);
		System.out.println("Containing: ");
		for (int j = 0; j < msg.length; j++) {
			System.out.println("byte " + j + " " + msg[j]);
		}
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
		System.out.println("Client#"+number+" -- send DATA with Block#"+
				unsignedShortToInt(block)+", to port "+port);
	}
	
	public void sendError(DatagramSocket socket, InetAddress address, int port, Reactor.Error error, String str_Explin){
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
			socket.send(new DatagramPacket(msg, msg.length, address, port));
		} catch (IOException e) {
			if(!EXCEPs_SILENT)
				e.printStackTrace();
			stop();
			return;
		}
		
		System.out.println("Client#"+number+" -- sendError to port "+port+", with Error ("+
				error+") and explination: "+str_Explin);
	}
	
	public boolean errorDispatcher(DatagramSocket socket, connectProfile info){
		if(socket != null && info != null && info.isError()){
			sendError(socket, info.getAddress(), info.getPort(), info.getError(), info.getExplinantion());
			return true;
		}
		return false;
	}
	
	public DatagramPacket receiveData(DatagramSocket socket, DatagramPacket p){
		int count = 0;
		while (count < ResentTrials){
			try {
				count++;
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- receiveData: is trying to receive a DATA.");
				socket.receive(p);
			} catch (SocketTimeoutException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- receiveData: Expecting data, but it's time out try again. Try#"+count);
				continue;
			} catch (IOException e) {
				if(!EXCEPs_SILENT)
					e.printStackTrace();
				if(!EXPLANs_SILENT)
					System.out.println("Client#"+number+" -- receiveData: Expecting data, but it's I/O Exception. Is shutting down reactor.");
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
			System.out.println("Client#"+number+" -- receiveData: Finished trying receiving data, is giving up! Now is shutting down reactor. Try#:"+count);
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
	
	public static final int unsignedShortToInt(byte b1, byte b2){
		byte[] temp = {b1,b2};
		return unsignedShortToInt(temp);
	}
	
	public static final int unsignedShortToInt(byte[] b) 
	{
	    int i = 0;
	    i |= b[0] & 0xFF;
	    i <<= 8;
	    i |= b[1] & 0xFF;
	    return i;
	}
	
	public static void main(String args[]) {
        while(true){
        	String choice = null;
        	
            System.out.println("Please choose: [1]SendRRQ [2]SendWRQ  [3]Quit");
            Scanner user_input = new Scanner( System.in );
            choice = user_input.next();
            
            
            if(choice.equalsIgnoreCase("3")){
                break;
            }
            else if(choice.equalsIgnoreCase("1")){
            	final String input;
                final String input2;
                System.out.print("Enter a file name to write to:");
                user_input = new Scanner( System.in );
                input = user_input.nextLine();
                System.out.print("Enter a file name to read from:");
                user_input = new Scanner( System.in );
                input2 = user_input.nextLine();
                System.out.println("File to write: "+input +" || File to read from is: "+input2);
                
           //     new Thread(new Runnable(){
                //
			//		public void run(){
						TFTPClient c = new TFTPClient();
				        try {
							c.sendRRQ(input, input2, "netascii", InetAddress.getLocalHost(), c.sendPort);
						} catch (UnknownHostException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
		//			}
		//		}).start();        
            }
            else if(choice.equalsIgnoreCase("2")){
            	final String input;
                final String input2;
                
                System.out.print("Enter a file name to write to:");
                user_input = new Scanner( System.in );
                input = user_input.nextLine();
                System.out.print("Enter a file name to read from:");
                user_input = new Scanner( System.in );
                input2 = user_input.nextLine();
                System.out.println("File to write: "+input +" || File to read from is: "+input2);
          //      new Thread(new Runnable(){
			//		public void run(){
						TFTPClient c = new TFTPClient();
						try {
							c.sendWRQ(input2, input, "netascii", InetAddress.getLocalHost(), c.sendPort);
						} catch (UnknownHostException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			//		}
		//		}).start();        
            }
           
            else{
                System.out.println("You have entered a wrong choice. Please try again");
            }
        }
       
    }
}

