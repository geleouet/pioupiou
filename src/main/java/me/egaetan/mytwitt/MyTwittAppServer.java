package me.egaetan.mytwitt;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.bouncycastle.crypto.generators.BCrypt;
import org.eclipse.jetty.http.HttpStatus;
import org.h2.tools.Server;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.FieldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;

public class MyTwittAppServer {

	public static class Message {
		public Integer id;
		public int idAuthor;
		public String message;
		public Instant timestamp;
		
		public Message() {
		}
		
		public Message(int id, int idAuthor, String message, Instant timestamp) {
			this.id = id;
			this.idAuthor = idAuthor;
			this.message = message;
			this.timestamp = timestamp;
		}

		public Message(int idAuthor, String message, Instant now) {
			this.idAuthor = idAuthor;
			this.message = message;
			this.timestamp = now;
		}
	}
	
	public static class Author {
		public Integer id;
		public String name;
	}

	public static class RegisterRequest {
		public String username;
		public String pseudo;
		public String password;
	}

	public static class Login {
		public Integer id;
		public String username;
		public String password;
		public String salt;
	}

	public static class TimeMessage {
		public String name;
		public String message;
		public String time;
		
	}
	
	public static class TimeLine {
		public List<TimeMessage> messages;

		public TimeLine(List<TimeMessage> timeline) {
			this.messages = timeline;
		}

	}
	
	public static class Follow {
		public int idAuthor;
		public int idFollower;
		
		public Follow() {
		}
		
		public Follow(int idAuthor, int idFollower) {
			super();
			this.idAuthor = idAuthor;
			this.idFollower = idFollower;
		}
	}
	
	
	
	public static Message save(Message message, Jdbi jdbi) {
		return jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(Message.class))
		.createUpdate("INSERT INTO Message(idAuthor, message, time) VALUES"
				+ " (:idAuthor, :message, :timestamp)")
		.bindFields(message)
		.executeAndReturnGeneratedKeys("id")
		.mapTo(Message.class)
		.one()
		);
	}

	
	
	public static Author register(RegisterRequest author, Jdbi jdbi) {

		String salt = new SecureRandomString().generate();
		
		return jdbi.withHandle(h->
		h.inTransaction(handle -> {
			int cost = 6;
			String password = author.password;
			byte[] generate = BCrypt.generate(BCrypt.passwordToByteArray(password.toCharArray()), 
					Base64.getDecoder().decode(salt.getBytes()), cost);
			String pwd = Base64.getEncoder().encodeToString(generate);
			;
			
			int id = handle.registerRowMapper(FieldMapper.factory(Login.class))

					.createUpdate("INSERT INTO Login(username, password, salt) VALUES"
							+ " (:name, :password, :salt)")
					.bind("name", author.username)
					.bind("password", pwd)
					.bind("salt", salt)
					.executeAndReturnGeneratedKeys("id")
					.mapTo(Login.class)
					.one()
					.id;


			return handle.registerRowMapper(FieldMapper.factory(Author.class))

					.createUpdate("INSERT INTO Author(id, name) VALUES"
							+ " (:id, :name)")
					.bind("id", id)
					.bind("name", author.pseudo)
					.executeAndReturnGeneratedKeys("id")
					.mapTo(Author.class)
					.one();

		}
				));
	}

	public static Optional<Author> login(String name, String password, Jdbi jdbi) {
		
		Optional<Login> saltInDb = jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(Login.class))
		.createQuery("SELECT id, salt FROM Login WHERE username LIKE :name")
		.bind("name", name)
		.mapTo(Login.class)
		.findOne()
				);
		if (saltInDb.isEmpty()) {
			return Optional.empty();
		}
		
		int cost = 6;
		int id = saltInDb.get().id;
		String salt = saltInDb.get().salt;
		byte[] generate = BCrypt.generate(BCrypt.passwordToByteArray(password.toCharArray()), 
				Base64.getDecoder().decode(salt.getBytes()), cost);
		String pwd = Base64.getEncoder().encodeToString(generate);
		
		
		Optional<Login> logggedIn = jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(Login.class))
		.createQuery("SELECT id FROM Login WHERE id = :id AND password LIKE :pwd")
		.bind("id", id)
		.bind("pwd", pwd)
		.mapTo(Login.class)
		.findOne()
				);
		
		if (logggedIn.isEmpty()) {
			return Optional.empty();
		}
		
		return jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(Author.class))
		.createQuery("SELECT id, name FROM Author WHERE id LIKE :id")
		.bind("id", id)
		.mapTo(Author.class)
		.findOne()
				);
		
	}
	

	public static List<TimeMessage> timeline(int id, Jdbi jdbi) {
		return jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(TimeMessage.class))
		.createQuery(
				"select top 100 author.name, message.message, message.time from message" + 
				" inner join author on author.id = message.idauthor" + 
				" inner join follow on follow.idauthor = author.id" + 
				" where follow.idfollower = :id" + 
				" order by time desc")
		.bind("id", id)
		.mapTo(TimeMessage.class)
		.list()
				);
	}
	
	
	public static void save(Follow follow, Jdbi jdbi) {
		jdbi.withHandle(handle -> 
		handle.createUpdate("INSERT INTO Follow(idAuthor, idFollower) VALUES"
				+ " (:idAuthor, :idFollower)")
		.bind("idAuthor", follow.idAuthor)
		.bind("idFollower", follow.idFollower)
		.execute());
	}
	
	
	public static void init(String url) {
		try {
			Connection conn = DriverManager.getConnection(url, "", "");
			conn.createStatement().execute(" " + 
					"create table Message ( " + 
					"	id  number primary key auto_increment, " + 
					"	idAuthor  number, " + 
					"	message  varchar(140), " + 
					"	time timestamp) ");
			
			
			conn.createStatement().execute(" " + 
					"create table Author ( " + 
					"	id  number primary key auto_increment, " + 
					"	name  varchar(140) " + 
					"	) ");
			
			conn.createStatement().execute(" " + 
					"create table Follow ( " + 
					"	idAuthor  number, " + 
					"	idFollower  number " + 
					"	) " + 
					"		");
			
			conn.createStatement().execute(" " + 
					"create table Login ( " +
					"	id  number primary key auto_increment, " + 
					"	username varchar(140), " + 
					"	password varchar(140), " + 
					"	salt varchar(140) " + 
					"	) " + 
					"		");
			
			conn.commit();
			conn.close();
		} catch (SQLException e) {
			// Silently fail
		}
	}
	
	
	public static void main(String[] args) {

		((ch.qos.logback.classic.Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.INFO);
		
		String databaseUrl = "jdbc:h2:tcp://localhost:9123/~/db/mytwitt";
		String dbUser="";
		String dbPassword="";
		
		Jdbi jdbi = startDB(databaseUrl, dbUser, dbPassword);

		int port = Integer.parseInt(System.getProperty("port", "8081"));
		
		Javalin app = Javalin.create(config -> {
			config.addStaticFiles("/public");
		});
		
		app.post("/register", ctx -> {
			RegisterRequest request = new RegisterRequest();
			request.username = ctx.formParam("username");
			request.password = ctx.formParam("password");
			request.pseudo = ctx.formParam("pseudo");
			register(request, jdbi);
		});
		app.post("/login", ctx -> {
			String name = ctx.formParam("username");
			String password = ctx.formParam("password");
			Optional<Author> login = login(name, password, jdbi);
			if (login.isPresent()) {
				ctx.cookie("id", "" + login.get().id);
				return;
			}
			else {
				ctx.status(HttpStatus.FORBIDDEN_403);
				ctx.result("Invalid");
			}
		});
		app.post("/message", ctx -> {
			String message = ctx.body();
			int authorId = Integer.parseInt(ctx.cookie("id"));
			Message m = new Message(authorId, message, Instant.now());
			save(m, jdbi);
			ctx.status(HttpStatus.CREATED_201);
		});
		app.get("/timeline", ctx -> {
			int idFollower = Integer.parseInt(ctx.cookie("id"));;
			List<TimeMessage> timeline = timeline(idFollower, jdbi);
			ctx.json(new TimeLine(timeline));
			
		});
		app.post("/follow/:id", ctx -> {
			int idFollower = Integer.parseInt(ctx.cookie("id"));;
			int idAuthor = ctx.pathParam("id", Integer.class).get();
			Follow follow = new Follow(idAuthor, idFollower);
			save(follow, jdbi);
			ctx.status(HttpStatus.CREATED_201);
		});

		
		app.start(port);
	}

	private static Jdbi startDB(String databaseUrl, String dbUser, String dbPassword) {
		try {
			Server.createWebServer("-webPort", "9124", "-ifNotExists", "-trace").start();
			Server.createTcpServer("-tcpPort", "9123", "-ifNotExists").start();
			System.out.println("Local db started");

			try {
				init(databaseUrl);
				System.out.println("InitDB");
			} catch (Exception e) {
				// silently fail
			}

			return Jdbi.create(databaseUrl, dbUser, dbPassword);
		} catch (SQLException e1) {
			throw new RuntimeException(e1);
		}

		

	}

	public static class SecureRandomString {
		private static final SecureRandom random = new SecureRandom();
		private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

		public String generate() {
			while (true) {
				String res = generateBytes();
				try {
					Base64.getDecoder().decode(res.getBytes());
					return res;
				} catch (IllegalArgumentException e) {
				}
			}

		}
		public String generateBytes() {
			byte[] buffer = new byte[16];
			random.nextBytes(buffer);
			return encoder.encodeToString(buffer);
		}
	}	
	
}
