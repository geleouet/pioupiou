

create table Message (
	id  number primary key auto_increment,
	idAuthor  number,
	message  varchar(140),
	time timestamp) ;
	


create table Author (
	id  number,
	name  varchar(140)
	) ;
	
	
create table Follow (
	idAuthor  number,
	idFollower  number
	) ;
	
create table Login (
	id  number primary key auto_increment, 
	username varchar(140), 
	password varchar(140), 
	salt varchar(140) 
	);