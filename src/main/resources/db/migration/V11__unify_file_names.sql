UPDATE files
SET description = name
WHERE description IS DISTINCT FROM name;
