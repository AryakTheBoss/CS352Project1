

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.text.ParseException;
import java.util.TimeZone;
import java.net.SocketTimeoutException;
import java.util.Map;

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
       // super.run(); //PLACEHOLDER
        
        String request = "";//holds the initial request line
        String temp = ""; //used for reading in additional lines
        String restOfRequest = ""; //holds any lines after the initial request line
        DataOutputStream outToClient = null; //file stream to send data to the client
        
        //gets a file stream that will send data to the client
    	try {
            outToClient = new DataOutputStream(client.getOutputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }
        
        //get the HTTP request
        //initial line is stored in request
        //additional lines are stored in restOfRequest
        try {
        	BufferedReader inFromServer = new BufferedReader(new
        				InputStreamReader(client.getInputStream()));
        	
        	//checks if the client times out
        	try {
	        	request = inFromServer.readLine(); //initial line
	        	//temp = inFromServer.readLine(); //line after

                while ((temp = inFromServer.readLine()) != null) {
                    restOfRequest = temp + restOfRequest;
                }//will store everything after the initial line
	        
        	//tells client that they timed out
        	} catch (SocketTimeoutException ste) {
        		sendError("408 Request Timeout", outToClient);
            	return;
        	}
        	
        	//hopefully returning from a thread is allowed
        } catch (IOException ioe) {
        	System.err.println("HTTP/1.0 404 Not Found");
        	return;
        }
        
        
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
        
        //attempt to read the second argument as a file
    	File file = new File(initialLine[1].substring(1));
    	
    	//check if the file exists
    	if(!(file.getAbsoluteFile().exists())) {
    		
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
        if(!checkDate(restOfRequest, file) && !isHead) {
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
    			client.close();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
        	return;
        }
        
        //
        
        
        //Calls the particular command specified by the first token
        if(initialLine[0].equals("GET")) {
        	get(initialLine);
        } else if(initialLine[0].equals("POST")) {
        	post(initialLine, restOfRequest);
        } else if(initialLine[0].equals("HEAD")) {
        	head(initialLine);
        } else if(initialLine[0].equals("DELETE")) {
        	delete(initialLine);
        } else if(initialLine[0].equals("PUT")) {
        	put(initialLine);
        } else if(initialLine[0].equals("LINK")) {
        	link(initialLine);
        } else {
        	unlink(initialLine);
        }
        
    }
    /**
     * Gives error msg to client and print out the error
     */
    private void sendError(String msg, DataOutputStream outToClient) {
    	System.err.println("HTTP/1.0 " + msg + "\r\n");
    	
    	try {
			outToClient.writeBytes("HTTP/1.0 " + msg + "\r\n");
			client.close();
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
    public boolean checkDate(String headers, File file) {
    	//separates the first header from the rest of the headers
    	String [] arr = headers.split(" |\t", 2);
    	
    	
    	/*
    	//while the header is not the correct header, go to the next line and repeat.
    	//if we reach the end, there is no ifmodified header.
    	while(!(arr[0].matches("(.*)If-Modified-Since:(.*)")) && arr.length == 2) {
    		arr = arr[1].split("\n", 2); //removes that line, and goes to the next line, and splits that
    		if (arr.length == 1) { //if there is a header with no value, break
    			break;
    		}
    		arr = arr[1].split(" |\t", 2); //separates the first header from the rest of the headers
    		
    		
    	}
    	*/
    	
    	//TESTING
    	if(!(arr[0].equalsIgnoreCase("If-Modified-Since:"))) {
    		System.err.println("No param: " + arr[0]);
    		return true;
    	}
    	
    	//now arr[1] up to the next line contains the date. Throws away the other headers
    	arr = arr[1].split("\n", 2);
    	String date = arr[0];
    	
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
    public void get(String[] initialLine) {

    	//Creates the header for the file
    	String header = createHeader(initialLine);
        
        byte[] last = null;
        byte[] fileContent = null;

        System.err.println(header);
        
        //get the file contents
        try {
        	File f = new File(initialLine[1].substring(1));
            f = f.getAbsoluteFile();
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

    public boolean numCheck(String num){
        boolean isNumber = true;
        try {
            int x = Integer.parseInt(num);
        } catch (NumberFormatException e) {
            isNumber = false;
        }
        return isNumber;
    }
    
    /**
     * Will implement the POST method of HTTP protocol
     * @param initialLine
     */
    public void post(String[] initialLine, String restOfRequest) {
        String [] headers = restOfRequest.split("|\n");
        Map<String,String> env = System.getenv();
        env.put("SCRIPT_NAME", initialLine[1]);

        DataOutputStream outToClient = null;
        try {
            outToClient = new DataOutputStream(client.getOutputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean type = false;
        boolean length = false;

        for(int i  = 0; i < headers.length; i++){
            String [] temp = headers[i].split(" ");
            if(temp[0].equalsIgnoreCase("From:")){
                env.put("HTTP_FROM", temp[1]);
            }
            else if(temp[0].equalsIgnoreCase("User-Agent:")){
                env.put("HTTP_USER_AGENT", temp[1]);
            }
            else if(temp[0].equalsIgnoreCase("Content-Type:")){
                type = true;
            }
            else if(temp[0].equalsIgnoreCase("Content-Length:")){
                length = true;
                if(!numCheck(temp[1])){
                    sendError("411 Length Required", outToClient);
                }
                env.put("CONTENT_LENGTH", temp[1]);
            }
        }
        if(!type){
            sendError("500 Internal Service Error", outToClient);
        }
        else if(!length){
            sendError("411 Length Required", outToClient);
        }

        int x = headers.length;
        String parameters = headers[x-1];
        int count = 0;
        for(int j = 0; j < parameters.length(); j++){
            if(count != 0 && parameters.charAt(j) == '!'){
                parameters.replace(parameters.charAt(j), '\0');
            }
            if(parameters.charAt(j) == '!'){
                count++;
            }
        }

        String decoded = parameters;

    	String header = createHeader(initialLine);
    	
    	//create and initialize the commands String array
    	String[] commands = null;
    	
    	//run the commands and store the result
    	char[] output = runScript(commands);
    	String output2 = new String(output);
    	
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
            try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
            os.close();

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
    	String header = createHeader(initialLine);
    	
        System.out.println(header);
        
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
    private String createHeader(String[] initialLine) {
    	//Assumes legal request and that the file exists
        Date d = new Date();
        Calendar c = Calendar.getInstance();

        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        File f = new File(initialLine[1].substring(1));
        f = f.getAbsoluteFile();
        
        String header = "HTTP/1.0 200 OK"; //the initial header line
        String allow="",contentEncoding="",contentLength="",contentType="",expires="",lastModified=""; //head components
        if(initialLine[1].endsWith("html")||initialLine[1].endsWith("htm")){
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
    
        c.setTime(d);
        contentLength = "\r\nContent-Length: "+f.length(); //size of file in bytes
        lastModified = "\r\nLast-Modified: "+formatter.format(f.lastModified());
        c.add(Calendar.YEAR, 1);
        expires = "\r\nExpires: " + formatter.format(c.getTime());
        header += contentType + contentLength + lastModified + contentEncoding + allow + expires + "\r\n\r\n";
    	return header;
    }
    
    private char[] runScript(String[] commands) {
    	/*
    	 * The next try catch statement surrounds the running of a script
    	 * will take in the arguments given, run the specified file with those arguments, and return the result.
    	 */
    	char[] output = new char[10000];
    	try {
			Runtime rt = Runtime.getRuntime();
			Process proc = rt.exec(commands);
			
			//Reader for standard input from the process
			BufferedReader stdInput = new BufferedReader(new 
			     InputStreamReader(proc.getInputStream()));
			
			stdInput.read(output);
			
			//close the buffered reader
			stdInput.close();
    	} catch (IOException ioe){
    		ioe.printStackTrace();
    	}
    	
    	return output;
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
            os.close();
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
        super.interrupt(); //PLACEHOLDER
    }
}