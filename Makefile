
CXX = g++
JAVAC = javac

all: ON VN

ON: ON.cpp
	$(CXX) -Wall -Wextra -std=c++17 ON.cpp -o ON

VN: VN.java
	$(JAVAC) VN.java

clean:
	rm -f ON *.class
