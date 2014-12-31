import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Scanner;

public class Simulator {
	public static int PORT = 6800;
	public static int Thread_NO = 0;
	private int port;
	private boolean running;
	private static boolean SILENT = true; // Set SILENT to false if you want to
											// see the exception stack traces
	private int input = -1, firstInput = 1, secondInput = 1, pktNum = 2,
			delayTime = 2000, errInput1 = -1, errInput2 = 0;

	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;

	public Simulator(int p) {
		port = p;
	}

	public static void main(String[] args) {
		Simulator s = new Simulator(PORT);
		s.start();
	}

	public void stop() {
		System.out.println("Simulator: is shutting down.");
		running = false;
		receiveSocket.close();
	}

	public void start() {
		running = true;
		try {
			// Construct a datagram socket and bind it to port 68
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets.
			receiveSocket = new DatagramSocket(port);
		} catch (SocketException se) {
			if (!SILENT)
				se.printStackTrace();
			stop();
			return;
		}
		while (running) {
			// First get some info how simulation will be done
			getInputFromUser();
			// Construct a DatagramPacket for receiving packets up
			// to 516 bytes long (the length of the byte array).

			byte[] data = new byte[516];
			receivePacket = new DatagramPacket(data, data.length);

			System.out.println("\nSimulator: Waiting for packet.");
			// Block until a datagram packet is received from receiveSocket.
			try {
				receiveSocket.receive(receivePacket);
			} catch (IOException e) {
				if (!SILENT)
					e.printStackTrace();
				stop();
				return;
			}

			// Here we're creating a Reactor & initializing it without any
			// waiting
			Thread t = new Thread(new SimReactor(receivePacket, firstInput,
					secondInput, pktNum, delayTime, errInput1, errInput2));
			t.start();
		}

	}

	public void getInputFromUser() {
		while (input == -1) {
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						System.in));

				System.out
						.println("Please choose what do you want to simualte");
				System.out.println("[1] Client to Server [2] Server to Client");

				input = in.read() - 48;
				System.out.println();
				if (input != 1 && input != 2) {
					System.out.println("Please enter a valid input!");
					input = -1;
				}
			} catch (Exception ex) {
				if (!SILENT)
					ex.printStackTrace();
				stop();
				return;
			}
		}
		firstInput = input;
		input = -1;
		while (input != 1 && input != 2 && input != 3 && input != 4
				&& input != 5 && input != 6) {
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						System.in));

				System.out.println("Please choose test mode");
				System.out
						.println("[1] Normal mode		[2] Lose mode	[3] Delay mode");
				System.out
						.println("[4] Duplicate mode	[5] Error No 4	[6] Error No 5");

				input = in.read() - 48;
				System.out.println();
				if (input != 1 && input != 2 && input != 3 && input != 4
						&& input != 5 && input != 6) {
					System.out.println("Please enter a valid input!");
				}
			} catch (Exception ex) {
				if (!SILENT)
					ex.printStackTrace();
				stop();
				return;
			}
		}
		secondInput = input;
		input = -1;
		while (input < 0) {
			try {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						System.in));

				System.out.println("Please choose the packet No");

				input = in.read() - 48;
				System.out.println();
				if (input < 0 || (input == 0 & firstInput == 2)) {
					System.out.println("Please enter a valid input!");
					input = -1;
				}
			} catch (Exception ex) {
				if (!SILENT)
					ex.printStackTrace();
				stop();
				return;
			}
		}
		pktNum = input;
		input = -1;
		if (secondInput == 3 || secondInput == 4) {
			while (input < 0) {
				try {
					BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in));

					System.out
							.println("Please choose the delay time in seconds");

					input = in.read() - 48;
					System.out.println();
					if (input < 0) {
						System.out.println("Please enter a valid input!");
					}
				} catch (Exception ex) {
					if (!SILENT)
						ex.printStackTrace();
					stop();
					return;
				}
			}
			delayTime = input * 1000;
		}
		if (secondInput == 5) {
			while (input != 1 && input != 2 && input != 3 && input != 4) {
				try {
					BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in));

					System.out.println("Please choose one of the following");
					if (firstInput == 1) {
						System.out
								.println("[1] TFTP Error Read Request	[2] TFTP Error Write Request");
						System.out
								.println("[3] TFTP Error Data Request	[4] TFTP Error Ack Request");
					} else {
						System.out
								.println("[3] TFTP Error Data Request	[4] TFTP Error Ack Request");
					}

					input = in.read() - 48;
					System.out.println();
					if (input != 1 && input != 2 && input != 3 && input != 4) {
						System.out.println("Please enter a valid input!");
					}
				} catch (Exception ex) {
					if (!SILENT)
						ex.printStackTrace();
					stop();
					return;
				}
			}
			errInput1 = input;
			input = -1;
		}

		if (errInput1 == 1) {
			while (input != 1 && input != 2 && input != 3 && input != 4
					&& input != 5 && input != 6) {
				try {
					BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in));

					System.out
							.println("Please choose one of the following errors");
					System.out
							.println("[1] invalid TFTP opcode on RRQ 	[2] invalid filename");
					System.out
							.println("[3] no zero byte after filename	[4] invalid mode");
					System.out
							.println("[5] no zero byte after mode		[6] send packet longer than RRQ packet");

					input = in.read() - 48;
					System.out.println();
					if (input != 1 && input != 2 && input != 3 && input != 4
							&& input != 5 && input != 6) {
						System.out.println("Please enter a valid input!");
					}
				} catch (Exception ex) {
					if (!SILENT)
						ex.printStackTrace();
					stop();
					return;
				}
			}
			errInput2 = input;
			input = -1;
		}
		if (errInput1 == 2) {
			while (input != 1 && input != 2 && input != 3 && input != 4
					&& input != 5 && input != 6) {
				try {
					BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in));

					System.out
							.println("Please choose one of the following errors");
					System.out
							.println("[1] invalid TFTP opcode on WRQ  [2] invalid filename");
					System.out
							.println("[3] no zero byte after filename [4] invalid mode");
					System.out
							.println("[5] no zero byte after mode		[6] send packet longer than RRQ packet");

					input = in.read() - 48;
					System.out.println();
					if (input != 1 && input != 2 && input != 3 && input != 4
							&& input != 5 && input != 6) {
						System.out.println("Please enter a valid input!");
					}
				} catch (Exception ex) {
					if (!SILENT)
						ex.printStackTrace();
					stop();
					return;
				}
			}
			errInput2 = input;
			input = -1;
		}
		if (errInput1 == 3) {
			while (input != 1 && input != 2 && input != 3 && input != 4) {
				try {
					BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in));

					System.out
							.println("Please choose one of the following errors");
					System.out
							.println("[1] invalid TFTP opcode on Data [2] packet less than 4 bytes");
					System.out
							.println("[3] packet more than 516 bytes	[4] invalid block number");

					input = in.read() - 48;
					System.out.println();
					if (input != 1 && input != 2 && input != 3 && input != 4) {
						System.out.println("Please enter a valid input!");
					}
				} catch (Exception ex) {
					if (!SILENT)
						ex.printStackTrace();
					stop();
					return;
				}
			}
			errInput2 = input;
			input = -1;
		}
		if (errInput1 == 4) {
			while (input != 1 && input != 2 && input != 3 && input != 4
					&& input != 5 && input != 6) {
				try {
					BufferedReader in = new BufferedReader(
							new InputStreamReader(System.in));

					System.out
							.println("Please choose one of the following errors");
					System.out
							.println("[1] invalid TFTP opcode on ACK	[2] packet less than 4 bytes");
					System.out
							.println("[3] packet more than 4 bytes	[4] invalid block number");

					input = in.read() - 48;
					System.out.println();
					if (input != 1 && input != 2 && input != 3 && input != 4
							&& input != 5 && input != 6) {
						System.out.println("Please enter a valid input!");
					}
				} catch (Exception ex) {
					if (!SILENT)
						ex.printStackTrace();
					stop();
					return;
				}
			}
			errInput2 = input;
			input = -1;
		}
	}
}
