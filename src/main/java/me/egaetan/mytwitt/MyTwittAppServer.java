package me.egaetan.mytwitt;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.Cookie;

import org.bouncycastle.crypto.generators.BCrypt;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpStatus;
import org.h2.tools.Server;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.FieldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.plugin.rendering.template.TemplateUtil;

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

	
	
	public static Author register(RegisterRequest author, SecureRandomString secureRandomString, Jdbi jdbi) {

		String salt = secureRandomString.generate();
		
		return jdbi.withHandle(h->
		h.inTransaction(handle -> {
			int cost = 6;
			String password = author.password;
			byte[] generate = BCrypt.generate(BCrypt.passwordToByteArray(password.toCharArray()), 
					Base64.getDecoder().decode(salt.getBytes()), cost);
			String pwd = Base64.getEncoder().encodeToString(generate);
			;
			
			@SuppressWarnings("resource") // Why !?
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
	
	public static List<Author> autocomplete(Jdbi jdbi, String motif) {
		if (motif.length() < 3) {
			return Collections.emptyList();
		}
		return jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(Author.class))
		.createQuery("SELECT TOP 10 id, name FROM Author WHERE name LIKE :motif || '%'")
		.bind("motif", motif)
		.mapTo(Author.class)
		.list()
				);
	}
	

	public static List<TimeMessage> timeline(int id, Jdbi jdbi) {
		return jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(TimeMessage.class))
		.createQuery(	
				"with merged as ( " + 
				" select top 100 author.name, message.message, message.time from message " + 
				" inner join author on author.id = message.idauthor  " + 
				" inner join follow on follow.idauthor = author.id  " + 
				" where follow.idfollower = :id " + 
				" union " + 
				" select top 100 author.name, message.message, message.time from message " + 
				" inner join author on author.id = message.idauthor  " + 
				" where author.id = :id " + 
				" order by time desc " + 
				") select top 100  merged.name, merged.message, merged.time from merged order by time desc")
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
	
	public static class Session {
		public Author author;
		private String id;
		private String csrf;

		public Session(String id, Author author) {
			super();
			this.id = id;
			this.author = author;
		}

		public boolean invalid() {
			return author == null;
		}

		public boolean verifyCsrf(String csrf) {
			if (this.csrf == null) {
				return false;
			}
			return this.csrf.equals(csrf);
		}
		
	}
	
	public static class InMemorySessionStore {
		ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
		
		public void add(Session session) {
			sessions.put(session.id, session);
		}
		
		public Optional<Session> retrieve(String id) {
			return Optional.ofNullable(sessions.get(id));
		}
	}
	
	public static class Cookies {
		
		private final boolean isHttps;

		public Cookies(boolean isHttps) {
			super();
			this.isHttps = isHttps;
		}

		public void registerSession(Context ctx, String sessionId) {
			Cookie cookieSession = new Cookie("id", sessionId);
			cookieSession.setHttpOnly(true);
			cookieSession.setSecure(isHttps);
			cookieSession.setComment(HttpCookie.SAME_SITE_STRICT_COMMENT);
			ctx.cookie(cookieSession);
		}
	}
	
	public static class CsrfException extends RuntimeException {

		private static final long serialVersionUID = 5255026398994226667L;
	}
	public static class InvalidSessionException extends RuntimeException {
		
		private static final long serialVersionUID = 5255026398994226667L;
	}
	
	public static void checkCsrf(Context ctx, InMemorySessionStore sessionStore) {
		Optional<Session> session = sessionStore.retrieve(ctx.cookie("id"));
		if (session.isEmpty()) {
			throw new CsrfException();
		}
		checkCsrf(ctx, session.get());
	}
	
	public static void checkCsrf(Context ctx, Session session) {
		String csrf = ctx.formParam("csrf");
		if (!session.verifyCsrf(csrf)) {
			throw new CsrfException();
		}
	}
	public static void checkCsrfHeader(Context ctx, Session session) {
		String csrf = ctx.header("anti-csrf-token");
		if (!session.verifyCsrf(csrf)) {
			throw new CsrfException();
		}
	}
	
	public static Session retrieveValidSession(Context ctx, InMemorySessionStore sessionStore) {
		if (ctx.cookie("id") == null) {
			throw new InvalidSessionException();
		}
		Optional<Session> session = sessionStore.retrieve(ctx.cookie("id"));
		if (session.isEmpty() || session.get().invalid()) {
			throw new InvalidSessionException();
		}
		return session.get();
	}
	
	public static void main(String[] args) {

		((ch.qos.logback.classic.Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.INFO);
		
		String databaseUrl = "jdbc:h2:tcp://localhost:9123/~/db/mytwitt";
		String dbUser="";
		String dbPassword="";

		int port = Integer.parseInt(System.getProperty("port", "8081"));
		boolean isHttps = Boolean.parseBoolean(System.getProperty("https", "true"));
		
		Jdbi jdbi = startDB(databaseUrl, dbUser, dbPassword);
		SecureRandomString secureRandomString = new SecureRandomString();
		InMemorySessionStore sessionStore = new InMemorySessionStore();
		Cookies cookies = new Cookies(isHttps);
		
		Javalin app = Javalin.create(config -> {
			config.addStaticFiles("/public");
		});
		
		app.get("/", ctx -> {
			try {
				Session session = retrieveValidSession(ctx, sessionStore);
				
				String csrfToken = secureRandomString.generate();
				ctx.render("/templates/timeline.html", TemplateUtil.model("csrf_token", csrfToken, "name", session.author.name));
				
				session.csrf = csrfToken;
				sessionStore.add(session);
				
			} catch (InvalidSessionException e) {
				String csrfToken = secureRandomString.generate();
				ctx.render("/templates/index.html", TemplateUtil.model("csrf_token", csrfToken, "display_path", "questions"));
				
				Session session = new Session(secureRandomString.generateSessionId(), null);
				session.csrf = csrfToken;
				sessionStore.add(session);
				
				cookies.registerSession(ctx, session.id);
			}
		});
		
		app.get("/autocomplete/:pattern", ctx -> {
			retrieveValidSession(ctx, sessionStore);
			ctx.json(autocomplete(jdbi, ctx.pathParam("pattern")));
			
		});
		
		app.post("/register", ctx -> {
			checkCsrf(ctx, sessionStore);
			
			RegisterRequest request = new RegisterRequest();
			request.username = ctx.formParam("username");
			request.password = ctx.formParam("password");
			request.pseudo = ctx.formParam("pseudo");
			register(request, secureRandomString, jdbi);
			
			ctx.redirect("/");
		});
		
		app.get("/logout", ctx -> {
			ctx.removeCookie("id");
			ctx.redirect("/");
		});
		app.post("/login", ctx -> {
			checkCsrf(ctx, sessionStore);
			
			String name = ctx.formParam("username");
			String password = ctx.formParam("password");
			Optional<Author> login = login(name, password, jdbi);
			if (login.isPresent()) {
				Session session = new Session(secureRandomString.generateSessionId(), login.get());
				sessionStore.add(session);
				cookies.registerSession(ctx, session.id);
				ctx.redirect("/");
				return;
			}
			else {
				ctx.status(HttpStatus.FORBIDDEN_403);
				ctx.result("Invalid");
			}
		});
		app.post("/message", ctx -> {
			Session session = retrieveValidSession(ctx, sessionStore);
			checkCsrf(ctx, session);
			String message = ctx.formParam("message");
			int authorId = session.author.id;
			Message m = new Message(authorId, message, Instant.now());
			save(m, jdbi);
			ctx.redirect("/");
		});
		app.get("/timeline", ctx -> {
			Session session = retrieveValidSession(ctx, sessionStore);
			int idFollower = session.author.id;
			List<TimeMessage> timeline = timeline(idFollower, jdbi);
			ctx.json(new TimeLine(timeline));
			
		});
		app.post("/follow/:id", ctx -> {
			Session session = retrieveValidSession(ctx, sessionStore);
			checkCsrfHeader(ctx, session);
			int idFollower = session.author.id;
			int idAuthor = ctx.pathParam("id", Integer.class).get();
			Follow follow = new Follow(idAuthor, idFollower);
			save(follow, jdbi);
			ctx.status(HttpStatus.CREATED_201);
		});

		app.exception(CsrfException.class, (e, ctx) -> {
			ctx.status(HttpStatus.UNAUTHORIZED_401);
		});
		app.exception(InvalidSessionException.class, (e, ctx) -> {
			ctx.status(HttpStatus.FORBIDDEN_403);
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

		public String generateSessionId() {
			return generate() + generate();
		}
		
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
