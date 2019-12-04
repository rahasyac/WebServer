import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * CSE 4344 
 * Lab # 1 Web Server Programming
 * Spring 2019
 * Name: FNU RAHASYA CHANDAN
 * 
 * Description:
 * The program implements the multi-thread Web Server
 * This server listens the TCP connections and serves
 * the connection parallel
 * Each connection will be handled in one thread.
 * 
 * The web server support the image resource too
 * 
 * The web server implements:
 * Protocol: HTTP 1.0
 * Response GET method
 * Response 200, 301, 404 code
 * 
 */
public final class WebServer extends Thread
{
	
	private static final int PORT = 8081; //default port
	
	
	private int serverPort; //server port
	
	
    // constructor @param port server port
	public WebServer(int port){
		this.serverPort = port;
	}
	
	
	private ServerSocket socket; // server socket
	
	/**
	 * main method to start web server
	 * @param argv argv[0] is server port
	 * otherwise, default port will be used
	 */
	public static void main(String argv[]) throws Exception
	{
		//server port
		int serverPort = PORT;
		
		//check argument
		if (argv.length == 1){
			try{
				serverPort = Integer.parseInt(argv[0]);
			}catch(Exception e){
				System.out.println("The provided port is not valid. Use default port: " + PORT);
			}
		}else if (argv.length > 1){
			System.out.println("Too many the arguments. Use default port: " + PORT);
		}
		
		//start the main thread
		(new WebServer(serverPort)).start();
		
		System.out.println("The web server is running on port " + serverPort);
	}
	
	/**
	 * this method is called by start method
	 * run the server in main thread
	 * This method will create many threads
	 * each thread serves for one connection
	 * after thread created, this method continues listen the new one
	 */
	public void run() {
		
		try {
			socket = new ServerSocket(serverPort);
		} catch (Exception e) {
			System.err.println("Error binding to port " + serverPort + ": " + e);
		}
		
		//Process HTTP service requests in an infinite loop.
		while (true) {
			try {
				// wait and listen for new client connection
				Socket clientSocket = socket.accept();
				
				// construct an object to process the HTTP request message.
				HttpRequest request = new HttpRequest(clientSocket);
				
				// start the thread.
				(new Thread(request)).start();
				
			} catch (Exception e) {
				//ignore
				break;
			}			
		}
	} 
}

/**
 * The HttpRequest class is created by the WebServer that
 * serves for one client connection
 * 
 */
final class HttpRequest implements Runnable
{
	/**
	 * new line character that is used in HTTP protocol
	 */
	final static String CRLF = "\r\n";

	/**
	 * TCP client socket
	 */
	private Socket socket;

	/**
	 * Constructor
	 * @param socket client socket
	 */
	public HttpRequest(Socket socket)
	{
		this.socket = socket;
	}

	/**
	 * Implement the run() method of the Runnable interface.
	 * This method is called by start method 
	 * 
	 */
	public void run()
	{
		//process the user request
		try {
			processRequest();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * process the user request
	 * It extracts the file name from the HTTP request
	 * then validate and process it (by reading file)
	 * This method handles the response with 200, 301, 404 code
	 * @throws Exception if error 
	 */
	private void processRequest() throws Exception
	{
		// Get a reference to the socket's input and output streams.
		InputStream is = socket.getInputStream();
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());

		// Set up input stream filters.
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
	
		// Get the request line of the HTTP request message.
		String requestLine = br.readLine();
	
		// Display the request line.
		System.out.println();
		System.out.println(requestLine);
	
		// Get and display the header lines.
		String headerLine = null;
		while ((headerLine = br.readLine()).length() != 0) {
			System.out.println(headerLine);
		}

		// Extract the filename from the request line.
		StringTokenizer tokens = new StringTokenizer(requestLine);
		
		String method = tokens.nextToken();  //  "GET" or "POST" or others
		
		// requested file input stream
		FileInputStream fis = null;
					
		boolean fileExists = true; //request file existing?
		
		// Construct the response message.
		String statusLine = null;
		String contentTypeLine = null;
		String entityBody = null;
		
		if (method.equals("GET")){ //get method
			
			String fileName = tokens.nextToken();
			
			// Prepend a "." so that file request is within the current directory.
			fileName = "." + fileName;
		
			// Open the requested file			
			try {
				fis = new FileInputStream(fileName);
			} catch (FileNotFoundException e) {
				fileExists = false;
			}
		
			//file exists, code is 200
			if (fileExists) {
				statusLine = "HTTP/1.1 200 OK" + CRLF;
				contentTypeLine = "Content-type: " + 
					contentType( fileName ) + CRLF;
			} else { //resource not found, code is 404
				statusLine = "HTTP/1.1 404 Not Found" + CRLF;
				contentTypeLine =  "Content-type: text/html" + CRLF;
				entityBody = "<HTML>" + 
					"<HEAD><TITLE>Not Found</TITLE></HEAD>" +
					"<BODY>Not Found</BODY></HTML>";
			}
			
		}else{//post or other method ?
			
			statusLine = "HTTP/1.1 303 See Other" + CRLF;
			contentTypeLine =  "Content-type: text/html" + CRLF;
			entityBody = "<HTML>" + 
				"<HEAD><TITLE>See Other</TITLE></HEAD>" +
				"<BODY>See Other</BODY></HTML>";
			
			fileExists = false; //request file not existing
		}
		
		// Send the status line.
		os.writeBytes(statusLine);
		
		// Send the content type line.
		os.writeBytes(contentTypeLine);

		// Send a blank line to indicate the end of the header lines.
		os.writeBytes(CRLF);
	
		// Send the entity body.
		if (fileExists)	{
			sendBytes(fis, os);
			fis.close();
		} else {
			os.writeBytes(entityBody);
		}
		
		
		// Close streams and socket.
		os.close();
		br.close();
		socket.close();
	}
	
	/**
	 * read from input stream and
	 * send bytes to output stream
	 * until end of file
	 * 
	 * @param fis input stream
	 * @param os output stream
	 * @throws Exception if read or write error
	 */
	private static void sendBytes(FileInputStream fis, OutputStream os) 
			throws Exception
	{
	   // Construct a 1K buffer to hold bytes on their way to the socket.
	   byte[] buffer = new byte[1024];
	   int bytes = 0;

	   // Copy requested file into the socket's output stream.
	   while((bytes = fis.read(buffer)) != -1 ) {
	      os.write(buffer, 0, bytes);
	   }
	}

	/**
	 * write the content based on the file name extension
	 * @param fileName file name
	 * @return content type
	 */
	private static String contentType(String fileName)
	{
		//htm or html
		if(fileName.endsWith(".htm") || fileName.endsWith(".html")){
			return "text/html";
		}
		
		//gif image
		if(fileName.endsWith(".gif")) {
			return "image/gif";
		}
		
		//jpeg image
		if(fileName.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		return "application/octet-stream";
	}
}

/**
 * Refrences
 *https://medium.com/@ssaurel/create-a-simple-http-web-server-in-java-3fc12b29d5fd
 *https://realpython.com/python-sockets/
 * instruction to use wireshark: http://www.cs.wayne.edu/fengwei/17sp-csc4992/labs/lab1-Instruction.pdf
 */

