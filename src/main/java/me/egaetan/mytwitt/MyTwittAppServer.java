package me.egaetan.mytwitt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

	public static Author save(Author author, Jdbi jdbi) {
		return jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(Author.class))
		.createUpdate("INSERT INTO Author(name) VALUES"
				+ " (:name)")
		.bindFields(author)
		.executeAndReturnGeneratedKeys("id")
		.mapTo(Author.class)
		.one()
				);
	}

	public static Optional<Author> login(String name, Jdbi jdbi) {
		return jdbi.withHandle(handle -> 
		handle.registerRowMapper(FieldMapper.factory(Author.class))
		.createQuery("SELECT id, name FROM Author WHERE name LIKE :name")
		.bind("name", name)
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
			Author author = ctx.bodyAsClass(Author.class);
			save(author, jdbi);
		});
		app.get("/login/:name", ctx -> {
			String name = ctx.pathParam("name");
			Optional<Author> login = login(name, jdbi);
			if (login.isPresent()) {
				ctx.cookie("id", "" + login.get().id);
				return;
			}
			else {
				ctx.status(HttpStatus.NOT_FOUND_404);
				ctx.result("No user with this name found");
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

	
	
}
