

create table Message (
	id  number primary key auto_increment,
	idAuthor  number,
	message  varchar(140),
	time timestamp) ;
	


create table Author (
	id  number primary key auto_increment,
	name  varchar(140)
	) ;
	
	
create table Follow (
	idAuthor  number,
	idFollower  number
	) ;
	
		