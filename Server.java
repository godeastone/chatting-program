package chatting_program;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Server implements Runnable {
	
	ServerSocket serversocket;
	Socket socket;
	BufferedReader reader1, reader2;
	PrintWriter writer;
	Thread t1, t2;
	volatile String in = "";
	volatile String out = "";
	static int PORT = 5678;
	KeyPair keypair_RSA = null;
	PrivateKey priv_RSA = null;
	PublicKey pub_RSA = null;
	volatile boolean start1 = true;
	volatile boolean exit = false;
	ObjectOutputStream outO = null;
	OutputStream os = null;
	ObjectInputStream inO = null;
	InputStream is = null;
	SecretKey sKey = null;
	byte[] encrypted_AESkey = null;
	static String iv = null;
	static byte[] encrypted_iv = null;
	static byte[] decrypted_iv = null;
	String encoded_pub_RSAKey = null;
	String encoded_priv_RSAKey = null;
	
	
	
	public static void main(String[] args) {
		new Server();
	}
	
	
	public Server() 
	{
		/**
		 * Create RSA Key Pair
		 */
		try {
			
			//Thread for write
			t1 = new Thread(this);
			
			//Thread for read
			t2 = new Thread(this);
			
			serversocket = new ServerSocket(PORT);
			
			System.out.println("Connecting...");			
			socket = serversocket.accept();
			
			System.out.println("##Connection success##\n");
			
			keypair_RSA = generate_RSA_key();
			pub_RSA = keypair_RSA.getPublic();
			priv_RSA = keypair_RSA.getPrivate();
			encoded_pub_RSAKey = Base64.getEncoder().encodeToString(pub_RSA.getEncoded());
			encoded_priv_RSAKey = Base64.getEncoder().encodeToString(pub_RSA.getEncoded());
			
			t1.start();
			t2.start();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public void run()
	{
		try {			
			if(Thread.currentThread() == t1) {
				/*
				 * Writing Thread
				 */
				
				//send RSA public key to client
				if(start1 = true) {
					os = socket.getOutputStream();
					outO = new ObjectOutputStream(os);
					outO.writeObject(pub_RSA);
					outO.flush();
					
					//Print RSA KeyPair
					System.out.println("[RSA KeyPair info generated by Server]");
					System.out.println(encoded_pub_RSAKey);
					System.out.println(encoded_priv_RSAKey + "\n");
					
					start1 = false;
				}
				
				while(true) {
					//Writing Chat

					reader1 = new BufferedReader(new InputStreamReader(System.in));
					writer = new PrintWriter(socket.getOutputStream(), true);
					
					//Add time stamp to entered message
					Long timeStamp = System.currentTimeMillis(); 
			        SimpleDateFormat sdf=new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
			        String sd = sdf.format(new Date(Long.parseLong(String.valueOf(timeStamp)))); 
			        
					in = reader1.readLine();
					if(exit == true)  break;
					
					String send = "\"" + in +"\"" + " " + sd;
					
					//Encrypt with AES256
					String send_encrypt = Encrypt_AES(send, sKey);
					
					//Send Encrypted Message to Client
				    writer.println(send_encrypt);
				    
				}
				
				
			} else {
				/*
				 * Reading Thread
				 */
				
				while(true) {
					
					if(encrypted_AESkey == null) {
						
						//receive encrypted AES key from client 
						is = socket.getInputStream();
						inO = new ObjectInputStream(is);
						encrypted_AESkey = (byte[])inO.readObject();
						String encryted_AESkey_string = 
								new String(Base64.getEncoder().encode(encrypted_AESkey));
						System.out.println("[encrypted AES key]");
						System.out.println(encryted_AESkey_string + "\n");
						
						//receive iv from client
						is = socket.getInputStream();
						inO = new ObjectInputStream(is);
						encrypted_iv = (byte[])inO.readObject();
						String encryted_IV_string = 
								new String(Base64.getEncoder().encode(encrypted_iv));
						System.out.println("[encrypted IV]");
						System.out.println(encryted_IV_string + "\n");
						
						
					} else {
						
						//decrypt secretkey & iv
						sKey = new SecretKeySpec(Decrypt_RSA(encrypted_AESkey, priv_RSA), "AES");
						String encodedsKey = Base64.getEncoder().encodeToString(sKey.getEncoded());
						decrypted_iv = Decrypt_RSA(encrypted_iv, priv_RSA);
						iv = new String(decrypted_iv, "UTF-8");
						
						//print AES secretKey & iv received from client
						System.out.println("[IV info From Client]");
						System.out.println(iv + "\n");
						System.out.println("[AES SecretKey info From Client]");
						System.out.println(encodedsKey + "\n");
						
						
						break;
					}
				}

				while(true) {
					//Reading Chat
					
					reader2 = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					out = reader2.readLine();
					
					String decrypted_out = Decrypt_AES(out, sKey);
					
					System.out.println("[From Server] " + decrypted_out);
					System.out.println("[Encrypted Message] " + "\"" + out + "\"");
					
					String[] temp = decrypted_out.split("\"");
					
					if(temp[1].equals("exit")) {
						//when Client send exit message
						
						//reader1 = new BufferedReader(new InputStreamReader(System.in));
						writer = new PrintWriter(socket.getOutputStream(), true);
						
						//Add time stamp to entered message
						Long timeStamp = System.currentTimeMillis(); 
				        SimpleDateFormat sdf=new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");
				        String sd = sdf.format(new Date(Long.parseLong(String.valueOf(timeStamp)))); 
				        
				        //send exit message to client
				        in = "exit";
						String send = "\"" + in +"\"" + " " + sd;
						
						//Encrypt with AES256
						String send_encrypt = Encrypt_AES(send, sKey);
						
						writer.println(send_encrypt);
						System.out.println("\n##Client try to close the connection##\n##Press Enter to close the connection##");
						exit = true;
						break;
					}
				}
			}
			
			
			if(Thread.currentThread() == t1) {
				socket.close();
				System.out.println("connection closed");
				System.out.println("##Program End##");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static KeyPair generate_RSA_key() {
		
		System.out.println("##Creating RSA Key Pair...##");
		
		KeyPair keyPair = null;
		
		try {
		    SecureRandom sr = new SecureRandom();
		    KeyPairGenerator generator;
		
		    generator = KeyPairGenerator.getInstance("RSA");
		    generator.initialize(2048, sr);
		
		    keyPair = generator.generateKeyPair();
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return keyPair;
	}
	
	
	public static byte[] Decrypt_RSA(byte[] encrypted, PrivateKey privateKey) {
		
		byte[] decrypted_RSA = null;
		
		try {
			
			Cipher cipher = Cipher.getInstance("RSA");
			
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			decrypted_RSA = cipher.doFinal(encrypted);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return decrypted_RSA;
	}
		
	
	public static String Encrypt_AES(String plaintext, SecretKey key)
	{
		String result = null;
		
		try {
		    
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
		    c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv.getBytes()));
		 
		    byte[] encrypted = c.doFinal(plaintext.getBytes("UTF-8"));
		    result = new String(Base64.getEncoder().encode(encrypted));
		 
		    
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	
	public static String Decrypt_AES(String ciphertext, SecretKey key)
	{
		String result = null;
		
		try {
		    
		    Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
		    c.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv.getBytes("UTF-8")));
		    
		    byte[] decrypted = Base64.getDecoder().decode(ciphertext.getBytes("UTF-8"));
		    result = new String(c.doFinal(decrypted), "UTF-8");
		 
		    
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
}