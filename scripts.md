# Catalogue scripts

Things you'll probably want to do on occasion, scripted.

## Requirements

* python3
* make
* [pip-tools](https://github.com/jazzband/pip-tools)

## What might you want to do?

### Removing a work

If you want to delete a work from the Catalogue API and prevent it reappearing:

```text
make remove_work catalogue_id=CATALOGUE_ID
```

