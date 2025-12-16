from pytest_bdd import then, parsers


@then('the work is invisible')
def the_work_is_invisible(work):
    assert work.type == "Invisible"


@then(parsers.parse('the work has the identifier "{identifier}"'))
def the_work_has_the_identifier(work, identifier):
    assert work.state is not None
    assert work.state.id() == identifier
