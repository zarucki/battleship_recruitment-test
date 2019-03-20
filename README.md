# Battleship rest backend

## Task Questions

**1. What technologies have you chosen to implement the backend? Why?**

I browsed your open-source projects, and noticed Bootzooka. Initially wanted
to use it as is, but seemed to complex. Though from the project I learned a
little bit what you expect. That's why I used `akka-http`. I already had some
experience with it but not much, and I like to at least learn something new
while doing recruitment tasks.

For session I decided to test drive your open-source project `akka-http-session`.

For json serialization and deserialization I picked `io.crice` also because of
previous familiarity.

Obviously for tests i needed `scalatest`.

**2. How much time did you spend on this task? Could you please divide this number into a few most
important areas?**

I would say around 15h?

I had to fight a little with `circe` a bit with case objects. 

Apart from that, I had to figure out how to test `akka-http`.

Obviously because of quite simple game logic most of the time was taken by 
architecture.

**3. How would you modify your application if the next feature to implement would 
be to allow players to place their ships on the gameboard? What new things 
need to be implemented to make it work?**

Placing ships is already implemented. Iwould have to add appropriate rest endpoints
and adjust interfaces a little bit.

**4. Assume that we have to fetch game ID from the external application. What challenges and
potential problems do you see and how would you prepare your application to handle them
properly?**

One player could be playing multiple games. If he does not specify in what game is
he/she making move I would have to guess.

If the player would refer to game by a name which then server has to look up. In 
that case, we would probably need to add some additional error handling maybe
retrying.

I guess one problem could be game id uniqueness?
