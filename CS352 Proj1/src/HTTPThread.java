

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.ParseException;
import java.util.TimeZone;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.ArrayList;

public class HTTPThread extends Thread {

    private Socket client;

    public HTTPThread(Socket client){
    this.client = client;

    }


    @Override
    public synchronized void start() {
        super.start(); //PLACEHOLDER
    	
    }


    @Override
    public void run(){
        
        String request = "";//holds the initial request line
        String temp = ""; //used for reading in additional lines
        String restOfRequest = ""; //holds any lines after the initial request line
        DataOutputStream outToClient = null; //file stream to send data to the client
        
        //get the HTTP request
        //initial line is stored in request
        //additional lines are stored in restOfRequest
        try {
        	BufferedReader inFromServer = new BufferedReader(new
        				InputStreamReader(client.getInputStream()));
        	
        	//checks if the client times out
        	try {
	        	request = inFromServer.readLine(); //initial line

	        	if(inFromServer.ready()) {
	        		restOfRequest = inFromServer.readLine();

	        	}
	        	
	        	//all other headers
                while (inFromServer.ready()) {
	        		if(restOfRequest.isEmpty()) {
	        			break;
	        		}
                	temp = inFromServer.readLine();
                    restOfRequest = restOfRequest + "\n" + temp;
                }

	        
        	//tells client that they timed out
        	} catch (SocketTimeoutException ste) {
        		
        		//check if request timed out
        		if(request == null || request == "") {
        			sendError("408 Request Timeout", outToClient);
        			return;
        		}
            	
        		//if if-statement is not triggered, we assume that information was successfully extracted
        	}
        	
        } catch (IOException ioe) {
        	System.err.println("HTTP/1.0 500 Internal Server Error");
        	return;
        }
        
        //Input from the Client
        System.err.println("-------------------------------------------------------------------------");
        System.err.println(request + "<-- initial line!");
        System.err.println(restOfRequest);
        
        ArrayList<String[]> headers = getHeaders(restOfRequest);
        
        //gets a file stream that will send data to the client
    	try {
            outToClient = new DataOutputStream(client.getOutputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }
        
        //parse HTTP request
        //splits up the initial line, checks it for errors, and then hands over the arguments to other methods
        String[] initialLine = request.split(" |\t");
        
        if (!initialLineErrorChecking(initialLine)) {
        	
        	sendError("400 Bad Request", outToClient);
        	return;
        }
        
        //check if the HTTP version is supported
        int endOfVersionNumber = initialLine[2].length(); //gets the length of the third token of the command line
    	String versionNumber = initialLine[2].substring(5, endOfVersionNumber); //gets the substring that is supposed to have the version number
    	
    	double version = Double.parseDouble(versionNumber);
        if(version > 1.0) {
        	sendError("505 HTTP Version Not Supported", outToClient);
        	return;
        }
        
        //checking for not implemented
        if(initialLine[0].equals("DELETE")) {
        	delete(initialLine);
        	return;
        } else if(initialLine[0].equals("PUT")) {
        	put(initialLine);
        	return;
        } else if(initialLine[0].equals("LINK")) {
        	link(initialLine);
        	return;
        } else if(initialLine[0].equals("UNLINK")) {
        	unlink(initialLine);
        	return;
        }
        
        //attempt to read the second argument as a file
    	File file = new File(initialLine[1]);

        if(initialLine[0].equals("POST")){
            if(!initialLine[1].endsWith("cgi")){
                sendError("405 Method Not Allowed", outToClient);
                return;
            }
        }
    	//check if the file exists
    	if(!(file.exists()) && !file.getPath().equals("/")) {
    		
    		sendError("404 Not Found", outToClient);
        	return;
    	}
    	
    	//see if the file is forbidden THIS ONLY WORKS ON LINUX!!
    	if(!(file.canRead())) {
    		sendError("403 Forbidden", outToClient);
        	return;
    	}
    	
    	//For dealing with the stupid case that HEAD doesnt care about ifModifiedSince 
    	boolean isHead = false;
    	if(initialLine[0].equals("HEAD")) {
    		isHead = true;
    	}
    	
        //checks if the file was modified or not
        if(!checkDate(headers, file) && !isHead) {
        	//Assumes legal request and that the file exists
            Calendar c = Calendar.getInstance();
            
            //Make a formatter and make a date set to a year from today
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            c.add(Calendar.YEAR, 1);
            
            //message
        	String msg = "HTTP/1.0 304 Not Modified\r\n"
        					+ "Expires: " + formatter.format(c.getTime()) + "\r\n";;
        	System.err.println(msg);
        	
        	byte[] realMsg = msg.getBytes();
        	
        	try {
    			outToClient.write(realMsg);
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
        	
        	closeConn();
        	return;
        }
        
        //Calls the particular command specified by the first token
        if(initialLine[0].equals("GET")) {
        	get(initialLine,headers);
        } else if(initialLine[0].equals("POST")) {
        	post(initialLine, headers);
        } else if(initialLine[0].equals("HEAD")) {
        	head(initialLine);
        }
        
    }
    
    /**
     * Gives error msg to client and print out the error
     * @param msg is the type of error, outToClient is the DataOutputStream so the client can see the error
     */
    private void sendError(String msg, DataOutputStream outToClient) {
    	System.err.println("HTTP/1.0 " + msg + "\r\n");
    	
    	try {
			outToClient.writeBytes("HTTP/1.0 " + msg + "\r\n");
			
			//this is causing exceptions!
			closeConn();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    /**
     * 
     * @param initialLine	holds the arguments in the initial line
     * @return true if no errors were encountered, false otherwise
     */
    private boolean initialLineErrorChecking(String[] initialLine) {
    	
    	if(initialLine.length != 3) {
    		return false;
    	}
    	
    	//checks if the first string is equal to any of these things
    	if(!(initialLine[0].equals("GET")
    			|| initialLine[0].equals("POST")
    			|| initialLine[0].equals("HEAD")
    			|| initialLine[0].equals("DELETE")
    			|| initialLine[0].equals("PUT")
    			|| initialLine[0].equals("LINK")
    			|| initialLine[0].equals("UNLINK"))) {
    		return false;
    	}
    	
    	//checks if the last string has HTTP/ in the front
    	//not sure if this will work
    	if(!(initialLine[2].matches("HTTP/(.*)"))) {
    		return false;
    	}
    	
    	//check if the last string has a number after the HTTP/
    	int endOfVersionNumber = initialLine[2].length(); //gets the length of the third token of the command line
    	String versionNumber = initialLine[2].substring(5, endOfVersionNumber); //gets the substring that is supposed to have the version number
    	
    	//try catch statement will check if the "version number" is a double
    	try {
    		Double.parseDouble(versionNumber);
    	} catch (Exception e) {
    		return false;
    	}
    	
    	return true;
    }
    
    /**
     * Will check if the file has been modified ever since the specified date
     * @param headers the string that holds the headers of the request, file is the file the request comes from
     * @return	boolean	if the file wasn't modified ever since the if-modified-since header date, returns true, false otherwise
     */
    public boolean checkDate(ArrayList<String[]> headers, File file) {
    	
    	String date = searchHeader(headers, "If-Modified-Since");
    	if(date == null) {
    		return true;
    	}
    	
    	//interpret this date
    	SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    	dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    	
    	//supposedly, the parse method is not threadsafe
    	//lock this line, and check for parsing errors.
    	//will turn the string into a date.
    	Date ifModified = null;
    	synchronized(this) {
    		try {
    			ifModified = dateFormat.parse(date);//line that is locked
    		} catch (ParseException pe) {
    			System.err.println("Error parsing date");
    			return true;//?? HUUUH?
    		}
    	}
    	
    	//get the date the file was last modified
    	Date modified = new Date(file.lastModified());
    	
    	//compare the dates, if the ifModified date is at or after modified date, return true
    	if ((ifModified.compareTo(modified)) < 0) {
    		return true;
    	}
    	
    	return false;//ifModified date is before the modified date, so the file has been modified
    }
    
    /**
     * Will implement the GET method of HTTP protocol
     * @param initialLine
     */
    public void get(String[] initialLine, ArrayList<String[]> headers) {

        //generates the appropriate headers
        String header = createHeader(initialLine,false,null);
        
        byte[] last = null;
        byte[] fileContent = null;
		boolean cookieValid = false;
        System.err.println(header);
        String cookieHeader = searchHeader(headers,"Cookie");
        String varName = "";
        String encodedDate ="";
		String decodedDateTime="";
        if(cookieHeader != null){
        	varName = cookieHeader.substring(0,cookieHeader.indexOf("="));
			encodedDate = cookieHeader.substring(cookieHeader.indexOf("=")+1);
			if(varName.equals("lasttime")){
				try {
					decodedDateTime = URLDecoder.decode(encodedDate, "UTF-8");
					cookieValid = true;
				} catch (UnsupportedEncodingException e) {
					//Cookie is invalid
					cookieValid = false;
				}
			}else{
				//also invalid
				cookieValid = false;
			}
			System.err.println(varName+" "+encodedDate);
		}
        //get the file contents
        try {
			File f = null;
        	if(cookieValid){
        		f = new File("index_seen.html");
			}else{
				f = new File("index.html");
			}
        	System.err.println(f.getPath()+" "+f.exists());

			FileInputStream fis = new FileInputStream(f);
			
			fileContent = new byte[(int)f.length()];
			fis.read(fileContent);
			fis.close();
			
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
        
        byte[] end = "\r\n".getBytes();
        last = new byte[fileContent.length + header.length() + end.length];
        System.arraycopy(header.getBytes(), 0, last, 0, header.length());
        System.arraycopy(fileContent, 0, last, header.length(), fileContent.length);
        System.arraycopy(end, 0, last, fileContent.length + header.length(), end.length);
        

        
        try {
            DataOutputStream os = new DataOutputStream(client.getOutputStream());
            os.write(last);
            os.flush();
            try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

        } catch (IOException e) {
            e.printStackTrace();
        }

        //closes the connection
        closeConn();
    }


    /**
     * Will implement the POST method of HTTP protocol
     * @param initialLine
     */
    public void post(String[] initialLine, ArrayList<String[]> headers) {
        DataOutputStream outToClient = null;
        try {
            outToClient = new DataOutputStream(client.getOutputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }
        
        boolean type = false;
        boolean length = false;
        
        
        if(searchHeader(headers, "Content-Length") != null) {
        	length = true;
        }
        
        if(searchHeader(headers, "Content-Type") != null) {
        	type = true;
        }
        
        if(!type){
            sendError("500 Internal Server Error", outToClient);
            return;
        }
        else if(!length){
            sendError("411 Length Required", outToClient);
            return;
        }
        
        //see if the file is forbidden THIS ONLY WORKS ON LINUX!!
    	File file = new File(initialLine[1].substring(1));
    	if(!(file.canExecute())) {
    		sendError("403 Forbidden", outToClient);
        	return;
    	}
        
        String param = searchHeader(headers, "Params");
        
        //Deconding params
        if(param != null) {
	        int y = param.length();
	        String tempParam = param;
	        param = "";
	        
	        //for loop for decoding params
	        for(int i = 0; i < y; i++) {
	        	if(tempParam.charAt(i) == '!') {
	        		i++;
	        		param = param + tempParam.charAt(i);
	        	} else {
	        		param = param + tempParam.charAt(i);
	        	}
	        }
        }
        
        //make and initialize the commands array
        String[] commands = new String[1];
        commands[0] = initialLine[1];
        
    	//run the commands and store the result
    	String output2 = runScript(commands, param, headers, initialLine);
    	//String output2 = new String(output);
    	
    	//check for 204
    	if((output2 == null) || output2.isEmpty()) {
    		sendError("204 No Content", outToClient);
    		return;
    	}
    	
    	
    	String header = createHeader(initialLine,true,output2.length() + "");
    	
    	//error checking for printing payload
    	System.err.println(header + output2);
    	
    	byte[] last = null;
    	byte[] fileContent = output2.getBytes();
    	byte[] end = "\r\n".getBytes();
        last = new byte[fileContent.length + header.length() + end.length];
        System.arraycopy(header.getBytes(), 0, last, 0, header.length());
        System.arraycopy(fileContent, 0, last, header.length(), fileContent.length);
        System.arraycopy(end, 0, last, fileContent.length + header.length(), end.length);
    	
        try {
            DataOutputStream os = new DataOutputStream(client.getOutputStream());
            os.write(last);
            os.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

        //closes the connection
        closeConn();
    }
    
    /**
     * Will implement the HEAD method of HTTP protocol
     * @param initialLine
     */
    public void head(String[] initialLine) {
    	
    	//Creates the header for the file
    	String header = createHeader(initialLine,false,null);
    	
        System.err.println(header + "--------------------------------------------------------------------------");
        
        try {
            DataOutputStream os = new DataOutputStream(client.getOutputStream());
            os.writeBytes(header);
            os.flush();
            try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

        } catch (IOException e) {
            e.printStackTrace();
        }

        //closes the connection
        closeConn();
    }


    
    /**
     * Will implement the DELETE method of HTTP protocol
     * @param initialLine
     */
    public void delete(String[] initialLine) {
    	unImplementedFunction();
    }
    
    /**
     * Will implement the PUT method of HTTP protocol
     * @param initialLine
     */
    public void put(String[] initialLine) {
    	unImplementedFunction();
    }
    
    /**
     * Will implement the LINK method of HTTP protocol
     * @param initialLine
     */
    public void link(String[] initialLine) {
    	unImplementedFunction();
    }
    
    /**
     * Will implement the UNLINK method of HTTP protocol
     * @param initialLine
     */
    public void unlink(String[] initialLine) {
    	unImplementedFunction();
    }
    
    /**
     * Creates the header for the HTTP responses based on the initial line and file.
     * @param initialLine first line of the HTTP request
     * @return header as a string (INCLUDING TWO CLRF's AT THE END!!!)
     */
    private String createHeader(String[] initialLine, boolean isPost, String nEWcontentLength) {
        DataOutputStream outToClient = null;
        try {
            outToClient = new DataOutputStream(client.getOutputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }
    	//Assumes legal request and that the file exists
        Date d = new Date();
        Calendar c = Calendar.getInstance();

        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		File f = null;
        if(initialLine[1].equals("/")){
        	f=new File("index.html");
		}else{
			f= new File(initialLine[1]);
		}
        
        String header = "HTTP/1.0 200 OK"; //the initial header line
        String allow="",contentEncoding="",contentLength="",contentType="",expires="",lastModified="",setCookie=""; //head components
        if(initialLine[1].endsWith("html")||initialLine[1].endsWith("htm") || isPost || initialLine[1].equals("/")){
            contentType = "\r\nContent-Type: text/html";
        }else if(initialLine[1].endsWith("txt")){
            contentType = "\r\nContent-Type: text/plain";
        }else if(initialLine[1].endsWith("gif")){
            contentType= "\r\nContent-Type: image/gif";
        }else if(initialLine[1].endsWith("png")){
            contentType= "\r\nContent-Type: image/png";
        }else if(initialLine[1].endsWith("jpg")){
            contentType= "\r\nContent-Type: image/jpeg";
        }else if(initialLine[1].endsWith("pdf")){
            contentType= "\r\nContent-Type: application/pdf";
        }else if(initialLine[1].endsWith("zip")){
            contentType= "\r\nContent-Type: application/zip";
        }else if(initialLine[1].endsWith("gz")){ //idk if that the extension that denotes x-gzip
            contentType= "\r\nContent-Type: application/x-gzip";
        }else{
            contentType = "\r\nContent-Type: application/octet-stream"; //unknown file type
        }

        contentEncoding="\r\nContent-Encoding: identity";
        allow = "\r\nAllow: GET, POST, HEAD";
            /*
            Change content length header (in the createHeader method) (requires you to change createHeader() method. (Aryak)
             */
        c.setTime(d);
        if(!isPost) {
            contentLength = "\r\nContent-Length: " + f.length(); //size of file in bytes
			System.err.println("used f.length() in createHeader()."+" For file: "+f.getPath()+" Length: "+f.length());
        }else{
            contentLength = "\r\nContent-Length: " +nEWcontentLength;
			System.err.println("used newContentLength in createHeader().");
        }
        lastModified = "\r\nLast-Modified: " + formatter.format(f.lastModified());
        c.add(Calendar.YEAR, 1);
        expires = "\r\nExpires: " + formatter.format(c.getTime());
		LocalDateTime myDateObj = LocalDateTime.now();
		DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String formattedDate = myDateObj.format(myFormatObj);
		System.out.printf("Formatted date+time %s \n",formattedDate);

		String encodedDateTime = null;
		try {
			encodedDateTime = URLEncoder.encode(formattedDate, "UTF-8");
			System.out.printf("URL encoded date-time %s \n",encodedDateTime);


		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		setCookie = "\r\nSet-Cookie: lasttime="+encodedDateTime;
        header += contentType + contentLength + lastModified + contentEncoding + allow + expires + setCookie + "\r\n\r\n";
    	return header;
    }
    
    /**
     * Runs the script given the parameters, environment, and command
     * @param commands command
     * @param param parameters
     * @param headers holds the environment
     * @param initialLine hold the request
     * @return string result.
     */
    private String runScript(String[] commands, String param, ArrayList<String[]> headers, String[] initialLine) {
    	/*
    	 * The next try catch statement surrounds the running of a script
    	 * will take in the arguments given, run the specified file with those arguments, and return the result.
    	 */
    	String msg = "";
    	String cmd = "." + commands[0];
    	try {
    		//make the process builder
    		//pb.command((List<String>)cmdline);
    		ProcessBuilder pb = new ProcessBuilder(cmd);
    		//pb.command(cmd);
    		Map<String, String> env = pb.environment();
    		makeEnvironment(headers, env, initialLine[1]);
    		Process proc = pb.start();
    		
    		//pass in parameters through standardin
    		if(param != null) {
    			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
	            bw.write(param);
	            bw.flush();
	            bw.close();
    		}
    		
			//Reader for standard input from the process
			BufferedReader stdInput = new BufferedReader(new 
			     InputStreamReader(proc.getInputStream()));
			
			//Reader for standard error for the process
			BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			System.err.println("##########################################################");
			String error = null;
			while((error = stdErr.readLine()) != null) {
				System.err.println(error);
			}
			System.err.println("##########################################################");
			
			String output = null;
			//Reader for standard input for the process
			while((output = stdInput.readLine()) != null) {
				msg = msg + output + "\n";
			}
			
			//close the buffered readers
			stdInput.close();
			stdErr.close();
    	} catch (IOException ioe){
    		ioe.printStackTrace();
    	}
    	
    	return msg;
    }
    
    /**
     * Sets up the environment for the process
     * @param headers
     * @return
     */
    private void makeEnvironment(ArrayList<String[]> headers, Map<String, String> env, String scriptName) {
    	//CONTENT_LENGTH (Search for Content-Length)
    	String ct = searchHeader(headers, "Content-Length");
    	if(ct != null) {
    		env.put("CONTENT_LENGTH", ct);
    	}
    	
    	//HTTP_FROM (search for From)
    	String hf = searchHeader(headers, "From");
    	if(hf != null) {
    		env.put("HTTP_FROM", hf);
    	}
    	
    	//HTTP_USER_AGENT (search for User-Agent)
    	String hua = searchHeader(headers, "User-Agent");
    	if(hua != null) {
    		env.put("HTTP_USER_AGENT", hua);
    	}
    	
    	//SERVER_NAME localhost
    	env.put("SERVER_NAME", client.getInetAddress().getHostAddress());
    	//SERVER_PORT (get this from the socket?
    	env.put("SERVER_PORT", client.getPort() + "");
    	//SCRIPT_NAME (pass in script name) //NEEDS THE FIRST BACKSLASH
    	env.put("SCRIPT_NAME", scriptName);
    	
    	
    	return;
    }
    
    /**
     * For all unimplemented HTTP functions
     * returns the error code 501
     */
    private void unImplementedFunction() {
    	try {
            DataOutputStream os = new DataOutputStream(client.getOutputStream());
            os.writeBytes("HTTP/1.0 501 Not Implemented\r\n");
            os.flush();
            try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

        } catch (IOException e) {
            e.printStackTrace();
        }
    	
    	closeConn();
    }
    
    /**
     * closes the socket connection, sends a server error if unsuccessful.
     */
    private void closeConn() {
    	
    	try {
            client.close(); //close the socket
        } catch (IOException e) {
            System.err.println("HTTP/1.0 500 Internal Server Error");
        }
        
    }
    
    @Override
    public void interrupt() {
        super.interrupt();
    }
    
    /**
     * gets the headers and returns them as an arraylist
     * @param headers
     * @return
     */
    private ArrayList<String[]> getHeaders(String headers) {
    	ArrayList<String[]> h = new ArrayList<String[]>();
    	
    	//gets the first header (if any)
    	String[] nextHeader = headers.split("\n", 2);
    	
    	//while loop goes through all headers and tries to split the header from the value
    	while(nextHeader.length != 1) {
    		String[] splitHeader = nextHeader[0].split(":", 2);
    		
    		//we are looking for the blank line! Then we can check if the next line has params. otherwise, we dont care
    		if(splitHeader[0].isEmpty()) {
    			
    			//read the next line and store it as params
				String[] header = new String[2];
				
				//stores the parameters
				header[0] = "Params";
				header[1] = nextHeader[1].trim();
				h.add(header);
				
    			break;
    		}
    		
    		//we assume that this line is a parameter, remove spaces around it.
    		splitHeader[1] = splitHeader[1].trim();
    		
    		//add this to the list
    		h.add(splitHeader);
    		
    		nextHeader = nextHeader[1].split("\n", 2);
    	}
    	
    	//we reached the end (presumably the blank line)
    	return h;
    }
    
    /**
     * Search method to get the header you want
     * @param headers list of all headers and their values
     * @param headerName header we are looking for 
     * @return value of that header
     */
    private String searchHeader(ArrayList<String[]> headers, String headerName) {
    	String ret = null;
    	
    	//search for the header
    	for(int i = 0; i < headers.size(); i++) {
    		String[] line = headers.get(i);
    		if(line[0].equalsIgnoreCase(headerName.trim())) {
    			ret = line[1];
    			break;
    		}
    	}
    	return ret;
    }
    
}