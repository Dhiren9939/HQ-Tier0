-- No dead letter: the serializer rejects bad events before they reach the outbox, and a row the relay cannot
-- publish halts the relay instead of being moved aside.
DROP TABLE access.outbox_dead_letter;
