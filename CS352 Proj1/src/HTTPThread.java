

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.text.ParseException;
import java.util.TimeZone;
import java.net.SocketTimeoutException;

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
        String temp; //used for reading in additional lines
        String restOfRequest; //holds any lines after the initial request line
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
	        	temp = inFromServer.readLine(); //line after
	        	restOfRequest = ""; //will store everything after the initial line
	        	
	        	boolean first = true;
	        	//get the rest of the response
	        	while(!(temp.isEmpty())) {
	        		
	        		//if there is a space or tab in the front, the line belongs to the previous header line
	        		if(temp.charAt(0) == '\t' || temp.charAt(0) == ' ' || first) {
	        			first = false;
	        			restOfRequest = restOfRequest + temp;
	        		
	        		//else, the line contains a new header line, so make a new line
	        		} else {
	        			restOfRequest = restOfRequest + "\n" + temp;
	        		}
	        		
	        		temp = inFromServer.readLine(); //line after
	        	}
	        
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
        
        //checks if the file was modified or not
        if(!checkDate(restOfRequest, file)) {
        	sendError("304 Not Modified", outToClient);
        	return;
        }
        
        //Calls the particular command specified by the first token
        if(initialLine[0].equals("GET")) {
        	get(initialLine);
        } else if(initialLine[0].equals("POST")) {
        	post(initialLine);
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
     * @param ifModified the string that holds the date.
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
    	if ((ifModified.compareTo(modified)) > 0) {
    		return true;
    	}
    	
    	return false;//ifModified date is before the modified date, so the file has been modified
    }
    
    /**
     * Will implement the GET method of HTTP protocol
     * @param initialLine
     */
    public void get(String[] initialLine) {

        //Assumes legal request and that the file exists
        Date d = new Date();
        Calendar c = Calendar.getInstance();

        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        File f = new File(initialLine[1].substring(1));
        f = f.getAbsoluteFile();
        
        
        String header = "HTTP/1.0 200 OK"; //the initial header line
        String body = "";
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
            contentType = "\r\nContent Type: application/octet-stream"; //unknown file type
        }

        contentEncoding="\r\nContent-Encoding: identity";
        allow = "\r\nAllow: GET, POST, HEAD";
    
        c.setTime(d);
            contentLength = "\r\nContent-Length: "+f.length(); //size of file in bytes
            lastModified = "\r\nLast-Modified: "+formatter.format(f.lastModified());
         c.add(Calendar.YEAR, 1);
         expires = "\r\nExpires: " + formatter.format(c.getTime());
        header += contentType + contentLength + lastModified + contentEncoding + allow + expires + "\r\n\r\n";
        

        System.err.println(header);
        
        //get the file contents
        try {
			FileInputStream fis = new FileInputStream(f);
			
			byte fileContent[] = new byte[(int)f.length()];
			fis.read(fileContent);
			body = new String(fileContent);
			fis.close();
			
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
        
        try {
            DataOutputStream os = new DataOutputStream(client.getOutputStream());
            os.writeBytes(header + body + "\r\n\r\n");
            os.flush();
            try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            client.close(); //close the socket
        } catch (IOException e) {
            System.err.println("HTTP/1.0 500 Internal Server Error");
        }
    }
    
    /**
     * Will implement the POST method of HTTP protocol
     * @param initialLine
     */
    public void post(String[] initialLine) {
        get(initialLine);
    }
    
    /**
     * Will implement the HEAD method of HTTP protocol
     * @param initialLine
     */
    public void head(String[] initialLine) {
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
            contentType = "\r\nContent Type: application/octet-stream"; //unknown file type
        }

        contentEncoding="\r\nContent-Encoding: identity";
        allow = "\r\nAllow: GET, POST, HEAD";
    
        c.setTime(d);
        contentLength = "\r\nContent-Length: "+f.length(); //size of file in bytes
        lastModified = "\r\nLast-Modified: "+formatter.format(f.lastModified());
        c.add(Calendar.YEAR, 1);
        expires = "\r\nExpires: " + formatter.format(c.getTime());
        header += contentType + contentLength + lastModified + contentEncoding + allow + expires + "\r\n\r\n";
        
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


        try {
            client.close(); //close the socket
        } catch (IOException e) {
            System.err.println("HTTP/1.0 500 Internal Server Error");
        }
    }


    
    /**
     * Will implement the DELETE method of HTTP protocol
     * @param initialLine
     */
    public void delete(String[] initialLine) {
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
        try {
        	
        	
            client.close(); //close the socket
        } catch (IOException e) {
            System.err.println("HTTP/1.0 500 Internal Server Error");
        }
    }
    
    /**
     * Will implement the PUT method of HTTP protocol
     * @param initialLine
     */
    public void put(String[] initialLine) {
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
        try {
            client.close(); //close the socket
        } catch (IOException e) {
            System.err.println("HTTP/1.0 500 Internal Server Error");
        }
    }
    
    /**
     * Will implement the LINK method of HTTP protocol
     * @param initialLine
     */
    public void link(String[] initialLine) {
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
        try {
            client.close(); //close the socket
        } catch (IOException e) {
            System.err.println("HTTP/1.0 500 Internal Server Error");
        }
    }
    
    /**
     * Will implement the UNLINK method of HTTP protocol
     * @param initialLine
     */
    public void unlink(String[] initialLine) {
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