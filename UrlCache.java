import java.net.*;
import java.util.*;
import java.lang.*;
import java.io.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * UrlCache Class
 * 
 * @author 	Majid Ghaderi
 * @author Emily Yin-Li Chow (modified)
 * @version	2.1, Oct 14, 2016
 *
 *
 * Known bugs:
 * - cannot download PDF files properly for some reason
 * - seemingly glitchy in complex HTML files
 *
 *
 */
public class UrlCache {
	private static FileInputStream catalogue_inFile = null;
	private static FileOutputStream catalogue_outFile = null;
	public HashMap cache = new HashMap();
	public File catalogFile;
	public File newDir, currentDir;
	public boolean preExistingCache = false; 
	public int num_byte_read = 0;
	public int off = 0;
	public int counter;
	public String http_response_header_string;
		
	//variables for HashMap
	public HashMap<String, Long> localCache = new HashMap<String, Long>(9000);
	public long modDateInCache;
	
	//variables for getObject()
	public Date theLastModDate_Date;
	
	
	//variables for searching existing cache
	public String retrievedLastModDateFCache_string;
	public SimpleDateFormat formatter2;
	public Date LastModDate_fCache;
	public long LastModDateFromCache_long;
	
	//variables for slicing off pieces of HTTP header
	public String[] LastModifiedPart_front;
	public String[] LastModifiedPart_back;
	public String Last_Mod_Date_fHeader;
	public String ModDateTemp;
	public SimpleDateFormat formatter;
	public Date LastModDate_fHeader;
	public long LastModDateFromHeader_long,LastMDFromHeader_long;
	public String[] ContentLengthPart_front;
	public String[] ContentLengthPart_back;
	public String lengthTemp;
	public String ContentLength_string;
	public long objectLength;
	
    /**
     * Default constructor to initialize data structures used for caching/etc
	 * If the cache already exists then load it. If any errors then throw exception.
	 *
     * @throws UrlCacheException if encounters any errors/exceptions
     */
	public UrlCache() throws UrlCacheException {
		try {
			//Tries to open existing 'catalog' file (if applicable)
			FileReader fileRead = new FileReader("catalog.txt");
			BufferedReader cacheBuffReader = new BufferedReader(fileRead);
			List<String> cacheArrayList = new ArrayList<String>();
			String line = null;
			//reads lines from file and put into ArrayList
			while ((line = cacheBuffReader.readLine()) != null) {
				cacheArrayList.add(line);
			}
			cacheBuffReader.close();
			
			String[] cacheArray = cacheArrayList.toArray(new String[]{});
			//Turn ArrayList into String[] such that it can be put into the HashMap localCache
			for (int i = 0; i < cacheArray.length; i++) {
				String [] splitCacheRow = cacheArray[i].split(" ",2);
				localCache.put(splitCacheRow[0], Long.parseLong(splitCacheRow[1]));
			}
			preExistingCache = true;
		} catch (IOException e) {
			//if an existing catalog file doesn't exist, set the boolean to false 
			preExistingCache = false;
		}
	}
	
	
	/**
	 * if mod date changed, this writes cache (HashMap) back into the catalog file to reflect the new date change
	 *
	 *@param url 	URL of the object that was downloaded.
	 *@param cacheNewDate 	the new modified date as retrieved from the server
	 *
	*/
	public void writeToCatalogFile () throws FileNotFoundException, IOException{
		//pulling the list of keys and values from the HashMap retrieval methods
		List<Object> list_keys = new ArrayList<Object>(localCache.keySet());
		List<Object> list_values = new ArrayList<Object>(localCache.values());
		//turning the list into a formal Object[] list to make inputting into file easier
		Object[] toPutBack_keys = list_keys.toArray(new Object[]{});
		Object[] toPutBack_values = list_values.toArray(new Object[]{});
		//initialize the catalogue file
		FileWriter out = new FileWriter("catalog.txt");
		//Write line by line the key + value in a form that will be easy for retrieval should we open it up again
		for (int i = 0; i < toPutBack_keys.length; i++) {
			out.write(toPutBack_keys[i] + " " + toPutBack_values[i] + "\r");
		}
		out.close(); //Finally close the stream
	}
	
	
    /**
     * Downloads the object specified by the parameter url if the local copy is out of date.
	 * Lots of code used from tutorials. Thank you Cyriac for your guidance on this assignment.
	 *
     * @param url	URL of the object to be downloaded. It is a fully qualified URL.
     * @throws UrlCacheException if encounters any errors/exceptions
     */
	public void getObject(String url) throws UrlCacheException {
		//URL parsing
		String [] URLparts = url.split("/",2);
		String host;
		String port;
		String filepath;
		if(URLparts[0].contains(":")) {
			String [] hostandport = URLparts[0].split(":",2);
			host = hostandport[0];
			port = hostandport[1];
			filepath = "/" + URLparts[1];
			url = hostandport[0] + "/" + URLparts[1];
		} else {
			host = URLparts[0];
			port = "80";
			filepath = "/" + URLparts[1];
		}
		
		//convert port number retrieved into an Integer
		int port_intForm = Integer.parseInt(port);
	
		//Socket opening
		Scanner inputStream;
		PrintWriter outputStream;
		try {
			Socket socket = new Socket(host, port_intForm);
			outputStream = new PrintWriter(new DataOutputStream(socket.getOutputStream()));
			inputStream = new Scanner(new InputStreamReader(socket.getInputStream()));
			//check if existing catalog/cache exists; if not, put new URL into cache
			if (preExistingCache == false) {
				localCache.put(url, (long)0);
			} else {
				//checks to see if existing catalog/cache has the url. if not, add.
				if (localCache.containsKey(url) == false) {
					localCache.put(url, (long)0);
				}
			}
			while (true) {
				if (preExistingCache == true) {  //conditional get method
					//retrieve date (in long) from the cache & convert it back into Date
					modDateInCache = localCache.get(url);
					theLastModDate_Date = new Date (modDateInCache * 1000);
					
					String requestLine_1 = "GET " + filepath + " HTTP/1.1\r\n";
					String requestLine_2 = "Host: " + host + ":" + port + "\r\n";
					String requestLine_3 = "If-modified-since: " + theLastModDate_Date + "\r\n";
					String eoh_line = "\r\n";
					try {
						String http_header = requestLine_1 + requestLine_2 + requestLine_3 + eoh_line;
						byte [] http_header_in_bytes = http_header.getBytes("US-ASCII");
						socket.getOutputStream().write(http_header_in_bytes);
						byte[] http_response_header_bytes = new byte[2048];
						byte[] http_object_bytes = new byte[1024];
						try {
							while (num_byte_read != -1) {
								socket.getInputStream().read(http_response_header_bytes, off, 1);
								off++;
								http_response_header_string = new String(http_response_header_bytes, 0, off, "US-ASCII");
								if (http_response_header_string.contains("\r\n\r\n")) {
									break;
								}
							}
						} catch (IOException e) {
							System.out.println("File download error");
						} 
						if (http_response_header_string.contains("304 Not Modified")) {
							inputStream.close();
							outputStream.close();
							writeToCatalogFile();
						} else if (http_response_header_string.contains("200 OK")) {
							counter = 0;
							//get object size from the header and save as integer objectLength
							ContentLengthPart_front = http_response_header_string.split("Content-Length: ",2);
							lengthTemp = ContentLengthPart_front[1];
							ContentLengthPart_back = lengthTemp.split("\r",2);
							ContentLength_string = ContentLengthPart_back[0];
							int objectLength_int = Integer.parseInt(ContentLength_string);
							//objectLength = objectLength_int.longValue();
							Long objectLength = new Long(objectLength_int);
							try {
								currentDir = folderBreakupAndMake(url, newDir, false); //make directories as necessary
								//creates new file inside this directory				
								File file = new File(url);
								InputStream inStream = socket.getInputStream();
								ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
								FileOutputStream fileOutStream = new FileOutputStream(file);
								while (num_byte_read != -1) {
									if (counter == objectLength) {
										break; //aka we don't read anymore
									}
									num_byte_read = socket.getInputStream().read(http_object_bytes);
									//System.out.println("NumBytesRead = " + num_byte_read);
									byteArrayOutStream.write(http_object_bytes, 0, num_byte_read);
									fileOutStream.write(http_object_bytes);		// write to file 'num_byte_read' bytes
									fileOutStream.flush();
									counter += num_byte_read;  // 'counter' incremented to total number of bytes read			
									
								}
								//closes all streams
								byteArrayOutStream.close();
								fileOutStream.close();
								inStream.close();
								inputStream.close();
								outputStream.close();
								writeToCatalogFile();
							} catch (IOException e) {
								//error in downloading file
								System.out.println("Error: " + e.getMessage());
							}
							outputStream.flush();
							break;
						}
					} catch (IOException e) {
						System.out.println("Error: " + e.getMessage());
					}
				} else { //else if first time catalog
					//regular GET situation
					String requestLine_1 = "GET " + filepath + " HTTP/1.1\r\n";
					String requestLine_2 = "Host: " + host + ":" + port + "\r\n";
					String eoh_line = "\r\n";
					try {
						String http_header = requestLine_1 + requestLine_2 + eoh_line;
						byte [] http_header_in_bytes = http_header.getBytes("US-ASCII");
						socket.getOutputStream().write(http_header_in_bytes);
						byte[] http_response_header_bytes = new byte[2048];
						byte[] http_object_bytes = new byte[1024];
						try {
							while (num_byte_read != -1) {
								socket.getInputStream().read(http_response_header_bytes, off, 1);
								off++;
								http_response_header_string = new String(http_response_header_bytes, 0, off, "US-ASCII");
								if (http_response_header_string.contains("\r\n\r\n")) {
									break;
								}
							}
						} catch (IOException e) {
							System.out.println("File download error");
						} 
						//retrieve modified date from header and put into cache
						LastMDFromHeader_long = retrieveModDatefromHeader(http_response_header_string);
						localCache.put(url, LastMDFromHeader_long);

						counter = 0;
						//get object size from the header and save as integer objectLength
						ContentLengthPart_front = http_response_header_string.split("Content-Length: ",2);
						lengthTemp = ContentLengthPart_front[1];
						ContentLengthPart_back = lengthTemp.split("\r",2);
						ContentLength_string = ContentLengthPart_back[0];
						int objectLength_int = Integer.parseInt(ContentLength_string);
						//objectLength = objectLength_int.longValue();
						Long objectLength = new Long(objectLength_int);
						try {
							currentDir = folderBreakupAndMake(url, newDir, false); //make directories as necessary
							//creates new file inside this directory				
							File file = new File(url);
							InputStream inStream = socket.getInputStream();
							ByteArrayOutputStream byteArrayOutStream = new ByteArrayOutputStream();
							FileOutputStream fileOutStream = new FileOutputStream(file);
							while (num_byte_read != -1) {
								if (counter == objectLength) {
									break; //aka we don't read anymore
								}
								num_byte_read = socket.getInputStream().read(http_object_bytes);
								//System.out.println("NumBytesRead = " + num_byte_read);
								byteArrayOutStream.write(http_object_bytes, 0, num_byte_read);
								fileOutStream.write(http_object_bytes);		// write to file 'num_byte_read' bytes
								fileOutStream.flush();
								counter += num_byte_read;  // 'counter' incremented to total number of bytes read			
								
							}
							//closing required streams
							byteArrayOutStream.close();
							fileOutStream.close();
							inStream.close();
						} catch (IOException e) {
							//error in downloading file
							System.out.println("Error: " + e.getMessage());
						}
						
						outputStream.flush();
					} catch (IOException e) {
						System.out.println("Error: " + e.getMessage());
						//code for Exception handling
					}
					break;
				}
			}	
			//closing streams
			inputStream.close();
			outputStream.close();
			writeToCatalogFile();
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
	}
		
	
    /**
     * Returns the Last-Modified time associated with the object specified by the parameter url.
	 *
     * @param url 	URL of the object 
	 * @return the Last-Modified time in millisecond as in Date.getTime()
     * @throws UrlCacheException if the specified url is not in the cache, or there are other errors/exceptions
     */
	public long getLastModified(String url) throws UrlCacheException {
		//go through cache to match variable url
		//if url matches cache entry, retrieve associated modifiedDate
		try {
			retrievedLastModDateFCache_string = "Wed, 4 May 1999 21:32:13 GMT"; //WILL BE EQUAL WHATEVER THE RETRIEVED VALUE IS
			formatter2 = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
			LastModDate_fCache = (Date)formatter2.parse(retrievedLastModDateFCache_string);
			LastModDateFromCache_long = LastModDate_fCache.getTime();
		} catch (ParseException e) {
			System.out.println("Error: " + e.getMessage());
		}
		return LastModDateFromCache_long;
	}

	/**
	 * Separates the path such that the necessary folders can be created and creates
	 * the path directory for the future file.
	 *
	 * @param newLastPath 	name of the path/files
	 * @param oldDirectoryName	name of the directory where the folders should be created
	 * @param noSourceDirectory 	Indication whether or not a source directory exists or not 
	 */
	public static File folderBreakupAndMake (String newLastPath, File oldDirectoryName, Boolean noSourceDirectory) {
		//Separates such that it knows how many subdirectories need to be created
		newLastPath = newLastPath.replace("\"", "");
		String[] splitAgain = newLastPath.split("/");
		String newDirectoryName = "";
		
		//runs if the last element does NOT contain "." in it
		if (!splitAgain[splitAgain.length-1].contains(".")) {
			for (int i = 0; i < splitAgain.length; i++) {
				newDirectoryName = splitAgain[i];
				//This assumes that we are making the folder in the primary directory
				if (noSourceDirectory == true) {
					File newDir = new File(newDirectoryName);
					if (!newDir.exists()) {
						newDir.mkdir();
					}
					noSourceDirectory = false;
					oldDirectoryName = newDir;
				} else {
					//Creates a new directory given that it is within another newly-created directory
					File newDir = new File(oldDirectoryName, newDirectoryName);
					newDir.mkdir();
					oldDirectoryName = newDir;
				}
			}
		//runs in all other cases
		} else {
			for (int i = 0; i < splitAgain.length-1; i++) {
				newDirectoryName = splitAgain[i];
				if (noSourceDirectory == true) {
					File newDir = new File(newDirectoryName);
					if (!newDir.exists()) {
						newDir.mkdir();
					}
					noSourceDirectory = false;
					oldDirectoryName = newDir;
				} else {
					//Creates a new directory given that it is within another newly-created directory
					File newDir = new File(oldDirectoryName, newDirectoryName);
					newDir.mkdir();
					oldDirectoryName = newDir;
				}
			}
		}
		newLastPath = splitAgain[splitAgain.length-1];
		newDirectoryName = newDirectoryName.replace("\\", "\\\\");
		return oldDirectoryName;
	}
	
	/**
	* This method cuts out the modified date from the header portion of the HTTP request
	*
	* @param http_response_header_string	the entire header as retrieved from the HTTP request 
	* @return LastModDateFromHeader_long
	*/
	public long retrieveModDatefromHeader (String http_response_header_string) throws ParseException {
		//get Last Modified Date of object
		LastModifiedPart_front = http_response_header_string.split("Last-Modified: ",2);
		ModDateTemp = LastModifiedPart_front[1];
		LastModifiedPart_back = ModDateTemp.split("\r",2);
		Last_Mod_Date_fHeader = LastModifiedPart_back[0];
		formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
		LastModDate_fHeader = (Date)formatter.parse(Last_Mod_Date_fHeader);
		LastModDateFromHeader_long = LastModDate_fHeader.getTime();
		return LastModDateFromHeader_long;
	}	
	
	
}
