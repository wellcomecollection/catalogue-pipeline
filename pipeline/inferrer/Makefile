.PHONY: requirements

requirements_files: common/requirements.txt palette_inferrer/requirements.txt feature_inferrer/requirements.txt aspect_ratio_inferrer/requirements.txt

%requirements.txt: %requirements.in
	pip-compile -o $@ $<
