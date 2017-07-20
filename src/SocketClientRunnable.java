import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;

/**
 * This is a socket client it will request for a socket connection to the cdc
 * server. After successful connectio of the socket, it will wait for the
 * freshness time. Which indicates upto which data has been captured. we have
 * two cdc, so there will be two threads of SocketClientRunnable running.
 * 
 * @author hadoop
 *
 */
public class SocketClientRunnable implements Runnable {



	private Socket socket = null;;
	private int portNumber;
	private String serverName;
	private DataInputStream inputStream;
	private DataOutputStream outputStream;
	private long requestTime = 0L;
	private long freshness = 0L;
	private long prevFreshness = 0L;
	private long recordedTime = 0L;
	//private long requestInterval = 0L;
	//private long timeWindow = 0L;


	public SocketClientRunnable(String serverName, int portNumber) {
		this.portNumber = portNumber;
		this.serverName = serverName;
		this.freshness = System.currentTimeMillis();
		this.prevFreshness = System.currentTimeMillis();
		try {
			// connect to the socket
			while (true) {
				try {
					socket = new Socket(this.serverName, this.portNumber);
					if (socket != null) {
						System.out.println("Successfully Connected to " + serverName);
						break;
					}
				} catch (IOException e) {
					try {
						System.out.println("Problem in connecting to server.");
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
			inputStream = new DataInputStream(socket.getInputStream());
			outputStream = new DataOutputStream(socket.getOutputStream());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		
		//read property file 
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream(System.getProperty("prop")));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		int noOfSet = Integer.parseInt(prop.getProperty("no_of_set"));
		int noOfExperiment = Integer.parseInt(prop.getProperty("no_of_experiment"));
		requestTime = System.currentTimeMillis();
		
		for(int i=0; i<=noOfSet; i++) {
			for(int j=1; j<= noOfExperiment; j++) {
				String experiment = prop.getProperty("experiment_"+j);
				String experimentParameter[] = experiment.split("_");
				long requestInterval = Long.parseLong(experimentParameter[1]);
				long timeWindow = Long.parseLong(experimentParameter[3]);
				// convert minute to mili second
				long runDuration = Long.parseLong(experimentParameter[5]) * 60000L;;
				long sessionEndTime = System.currentTimeMillis() + runDuration;
				System.out.println("Experiment : "+ experiment + " Request interval: "+ requestInterval + " Time Window: "+ timeWindow + " Run Duration: "+ runDuration);
				while (System.currentTimeMillis()< sessionEndTime) {
					try {
						// if (inputStream.available() != 0) {
						freshness = inputStream.readLong();
						recordedTime = System.currentTimeMillis();

						long numberOfRequest = ((recordedTime - requestTime) / requestInterval) + 1;

						for (int k = 0; k < numberOfRequest; k++) {
							long expirationTime = requestTime + timeWindow;
							long staleness = 0L;
							long latency = 0L;
							boolean expired = false;

							if (freshness >= requestTime && recordedTime <= expirationTime) {
								latency = recordedTime - requestTime;
								Status status = new Status(staleness, latency, expired);
								StatusWriterRunnable.requestStatusQueue.put(status);

								System.out.println("ServerName :" + serverName + " Freshness: " + freshness + " PrevFreshness: "+ prevFreshness +" Recorded T: "
										+ recordedTime + " Request T: " + requestTime + " Expiration T: " + expirationTime
										+ " Staleness: " + staleness + " Latency: " + latency + " Expired : " + expired);

								requestTime = requestTime + requestInterval;
							} else if (recordedTime > expirationTime) {
								latency = expirationTime - requestTime;
								staleness = requestTime - prevFreshness;
								expired = true;
								Status status = new Status(staleness, latency, expired);
								StatusWriterRunnable.requestStatusQueue.put(status);

								System.out.println("ServerName :" + serverName + " Freshness: " + freshness + " PrevFreshness: "+ prevFreshness +" Recorded T: "
										+ recordedTime + " Request T: " + requestTime + " Expiration T: " + expirationTime
										+ " Staleness: " + staleness + " Latency: " + latency + " Expired : " + expired);

								requestTime = requestTime + requestInterval;
							} else {
								break;
							}

						}

						prevFreshness = freshness;

					} catch (IOException | InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println("Problem in CDC requester");
						System.exit(1);
					}
					
				}
				System.out.println("End Experiment : "+ experiment + " Request interval: "+ requestInterval + " Time Window: "+ timeWindow + " Run Duration: "+ runDuration);
				
			}
		}
		
		
		while (true) {
			
		}

	}

}
