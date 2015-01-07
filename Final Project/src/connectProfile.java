
import java.net.InetAddress;


public class connectProfile {
	private Reactor.Request	request;
	private String			fileName = "None";
	private String			mode = "None";
	private Reactor.Error	error = Reactor.Error.None;	// No Error as an initial value
	private String			explinantion = "None";
	private int 			port;	
	private InetAddress 	address;
		
	
	

	public connectProfile (Reactor.Request req, String fn, String m){
		this.request = req;
		this.fileName = fn;
		this.mode = m;
	}
	
	public connectProfile (Reactor.Request req, InetAddress a, int p){
		this.request = req;
		this.address = a;
		this.port = p;
	}
	
	public connectProfile (Reactor.Request req, String fn, String m, InetAddress a, int p){
		this.request = req;
		this.fileName = fn;
		this.mode = m;
		this.address = a;
		this.port = p;
	}
	
	public connectProfile (Reactor.Request req, Reactor.Error er){
		this.request = req;
		this.error = er;
	}
	
	public connectProfile (Reactor.Request req, Reactor.Error er, String e){
		this.request = req;
		this.error = er;
		this.explinantion = e;
	}
	
	public connectProfile (Reactor.Request req, Reactor.Error er, String e, InetAddress a, int p){
		this.request = req;
		this.error = er;
		this.explinantion = e;
		this.address = a;
		this.port = p;
	}
	
	public connectProfile (Reactor.Request req, Reactor.Error er, InetAddress a, int p){
		this.request = req;
		this.error = er;
		this.address = a;
		this.port = p;
	}
	
	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public InetAddress getAddress() {
		return address;
	}

	public void setAddress(InetAddress address) {
		this.address = address;
	}


	
	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public Reactor.Error getError() {
		return error;
	}

	public void setError(Reactor.Error error) {
		this.error = error;
	}

	public Reactor.Request getRequest() {
		return request;
	}
	
	public String getExplinantion() {
		return explinantion;
	}
	
	public void setExplinantion(String explinantion) {
		this.explinantion = explinantion;
	}
	
	public boolean isError(){
		return !(this.error == Reactor.Error.None);
	}
	
	public String toString(){
		return new String ("File Name: ("+fileName+"), Mode: ("+mode+"), Error: ("+error+"), Explination: ("
				+explinantion+").");
	}
}
