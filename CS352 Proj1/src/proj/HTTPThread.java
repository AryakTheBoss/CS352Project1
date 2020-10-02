package proj;

import java.io.*;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.text.ParseException;
import java.util.Scanner;
import java.net.URLConnection;

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
        
        //get the HTTP request
        //initial line is stored in request
        //additional lines are stored in restOfRequest
        try {
        	BufferedReader inFromServer = new BufferedReader(new
        				InputStreamReader(client.getInputStream()));
        	
        	request = inFromServer.readLine(); //initial line
        	temp = inFromServer.readLine(); //line after
        	restOfRequest = ""; //will store everything after the initial line
        	
        	//get the rest of the response
        	while(!(temp.isEmpty())) {
        		
        		//if there is a space or tab in the front, the line belongs to the previous header line
        		if(temp.charAt(0) == '\t' || temp.charAt(0) == ' ') {
        			restOfRequest = restOfRequest + temp;
        		
        		//else, the line contains a new header line, so make a new line
        		} else {
        			restOfRequest = restOfRequest + "\n" + temp;
        		}
        		
        	}
        	
        	//hopefully returning from a thread is allowed
        } catch (IOException ioe) {
        	System.err.println("HTTP/1.0 404 Not Found");
        	return;
        }
        
        DataOutputStream outToClient = null;
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
        	
        	System.err.println("HTTP/1.0 400 Bad Request");
        	
        	try {
				outToClient.writeChars("HTTP/1.0 400 Bad Request");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	return;
        }
        
        //attempt to read the second argument as a file
    	File file = new File(initialLine[1]);
    	
    	//check if the file exists
    	if(!(file.exists())) {
    		System.err.println("HTTP/1.0 404 Not Found");
    		
    		try {
				outToClient.writeChars("HTTP/1.0 404 Not Found");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	return;
    	}
    	
    	//see if the file is forbidden THIS ONLY WORKS ON LINUX!!
    	if(!(file.canRead())) {
    		System.err.println("HTTP/1.0 403 Forbidden");
        	
        	try {
				outToClient.writeChars("HTTP/1.0 403 Forbidden");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	return;
    	}
        
        //checks if the file was modified or not
        if(checkDate(restOfRequest, file)) {
        	
        	System.err.println("HTTP/1.0 204 Not Modified");
        	
        	try {
				outToClient.writeChars("HTTP/1.0 204 Not Modified");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	return;
        }
        
        //check if the HTTP version is supported
        int endOfVersionNumber = initialLine[2].length(); //gets the length of the third token of the command line
    	String versionNumber = initialLine[2].substring(5, endOfVersionNumber); //gets the substring that is supposed to have the version number
    	
    	double version = Double.parseDouble(versionNumber);
        if(version > 1.0) {
        	System.err.println("HTTP/1.0 505 HTTP Version Not Supported");
        	
        	try {
				outToClient.writeChars("\"HTTP/1.0 505 HTTP Version Not Supported");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
    	String [] arr = headers.split(" ", 2);
    	
    	//while the header is not the correct header, go to the next line and repeat.
    	//if we reach the end, there is no ifmodified header.
    	while(!(arr[0].equalsIgnoreCase("If-Modified-Since:")) || arr.length == 1) {
    		arr = arr[1].split("\n", 2); //removes that line, and goes to the next line, and splits that
    		arr = arr[1].split(" ", 2); //separates the first header from the rest of the headers
    	}
    	
    	//if there is no ifModified specification, just return true
    	if(arr.length == 1) {
    		return true;
    	}
    	
    	//now arr[1] up to the next line contains the date. Throws away the other headers
    	arr = arr[1].split("\n", 2);
    	String date = arr[0];
    	
    	//interpret this date
    	SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    	
    	//supposedly, the parse method is not threadsafe
    	//lock this line, and check for parsing errors.
    	//will turn the string into a date.
    	Date ifModified = null;
    	synchronized(this) {
    		try {
    			ifModified = dateFormat.parse(date);//line that is locked
    		} catch (ParseException pe) {
    			System.err.println("Error parsing date");
    		}
    	}
    	
    	//get the date the file was last modified
    	Date modified = new Date(file.lastModified());
    	
    	//compare the dates, if the ifModified date is before modified date, return true
    	if ((ifModified.compareTo(modified)) < 0) {
    		return true;
    	}
    	
    	return false;
    }
    
    /**
     * Will implement the GET method of HTTP protocol
     * @param initialLine
     */
    public void get(String[] initialLine) {

        //Assumes legal request and that the file exists
        Date d = new Date();
        Calendar c = Calendar.getInstance();

        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        File f = new File(initialLine[1]);
        String header = "HTTP/1.0 200 OK"; //the initial header line
        String body = "";
        String allow="",contentEncoding="",contentLength="",contentType="",expires="",lastModified=""; //head components
        if(initialLine[1].endsWith("html")||initialLine[1].endsWith("htm")){
            contentType = "\nContent-Type: text/html";
            allow = "\nAllow: GET, HEAD, POST"; //not sure if post is allowed on html files
        }else if(initialLine[1].endsWith("txt")){
            contentType = "\nContent-Type: text/plain";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("gif")){
            contentType= "\nContent-Type: image/gif";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("png")){
            contentType= "\nContent-Type: image/png";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("jpg")){
            contentType= "\nContent-Type: image/jpeg";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("pdf")){
            contentType= "\nContent-Type: application/pdf";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("zip")){
            contentType= "\nContent-Type: application/zip";
            contentEncoding="\nContent-Encoding: zip";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("gz")){ //idk if that the extension that denotes x-gzip
            contentType= "\nContent-Type: application/x-gzip";
            allow = "\nAllow: GET, HEAD";
            contentEncoding="\nContent-Encoding: x-gzip";
        }else{
            contentType = "\nContent Type: application/octet-stream"; //unknown file type
            allow = "\nAllow: GET, HEAD";
        }
        c.setTime(d);
            contentLength = "\nContent-Length: "+f.length(); //size of file in bytes
            lastModified = "\nLast-Modified: "+formatter.format(f.lastModified());
         c.add(Calendar.YEAR, 1);
            expires = "\nExpires: "+formatter.format(c.getTime());
        header += allow+contentEncoding+contentLength+contentType+expires+lastModified+"\n";
        if(initialLine[1].endsWith("html")||initialLine[1].endsWith("htm")||initialLine[1].endsWith("txt")) {
            try {
                Scanner fr = new Scanner(f);

                while(fr.hasNextLine()){
                    body+=fr.nextLine();
                }
                try {
                    DataOutputStream os = new DataOutputStream(client.getOutputStream());
                    os.writeChars(header+body);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                System.err.println("File could not be Read");
            }
        }else{
            try {
                Scanner fr = new Scanner(f);

                while(fr.hasNextByte()){
                    body+=fr.nextByte();
                }
                try {
                    DataOutputStream os = new DataOutputStream(client.getOutputStream());
                    os.writeBytes(header+body);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                System.err.println("File could not be Read");
            }
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
    	
    }
    
    /**
     * Will implement the HEAD method of HTTP protocol
     * @param initialLine
     */
    public void head(String[] initialLine) {
        Date d = new Date();
        Calendar c = Calendar.getInstance();

        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        File f = new File(initialLine[1]);
        int fLength = (int) f.length();
        String header = "HTTP/1.0 200 OK"; //the initial header line
        String allow="",contentEncoding="",contentLength="",contentType="",expires="",lastModified=""; //head components

        /*possibly (emphasis on possibly) simpler way for below:
        URLConnection c = f.toURL().openConnection();
        String contentType = c.getContentType();//just returns a string mime type

        if(contentType.equals("text/html")){
            allow = "\Allow: GET, HEAD, POST";
        }else if(contentType.equals("application-zip")){
            contentEncoding="\nContent-Encoding: zip";
            allow = "\nAllow: GET, HEAD";
        }else if(contentType.equals("application/x-gzip")
            allow = "\nAllow: GET, HEAD";
            contentEncoding="\nContent-Encoding: x-gzip";
        else{
            allow = "\nAllow: GET, HEAD";
        }
        */


        if(initialLine[1].endsWith("html")||initialLine[1].endsWith("htm")){
            contentType = "\nContent-Type: text/html";
            allow = "\nAllow: GET, HEAD, POST"; //not sure if post is allowed on html files
        }else if(initialLine[1].endsWith("txt")){
            contentType = "\nContent-Type: text/plain";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("gif")){
            contentType= "\nContent-Type: image/gif";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("png")){
            contentType= "\nContent-Type: image/png";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("jpg")){
            contentType= "\nContent-Type: image/jpeg";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("pdf")){
            contentType= "\nContent-Type: application/pdf";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("zip")){
            contentType= "\nContent-Type: application/zip";
            contentEncoding="\nContent-Encoding: zip";
            allow = "\nAllow: GET, HEAD";
        }else if(initialLine[1].endsWith("gz")){ //idk if that the extension that denotes x-gzip
            contentType= "\nContent-Type: application/x-gzip";
            allow = "\nAllow: GET, HEAD";
            contentEncoding="\nContent-Encoding: x-gzip";
        }else{
            contentType = "\nContent Type: application/octet-stream"; //unknown file type
            allow = "\nAllow: GET, HEAD";
        }


        c.setTime(d);
        contentLength = "\nContent-Length: "+fLength; //size of file in bytes
        lastModified = "\nLast-Modified: "+formatter.format(f.lastModified());
        c.add(Calendar.YEAR, 1);
        expires = "\nExpires: "+formatter.format(c.getTime());
        header += allow+contentEncoding+contentLength+contentType+expires+lastModified+"\n";


                try {
                    DataOutputStream os = new DataOutputStream(client.getOutputStream());
                    os.writeChars(header);

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
            os.writeBytes("HTTP/1.0 501 Not Implemented");

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
            os.writeBytes("HTTP/1.0 501 Not Implemented");

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
            os.writeBytes("HTTP/1.0 501 Not Implemented");

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
            os.writeBytes("HTTP/1.0 501 Not Implemented");

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
