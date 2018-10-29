package org.openobservatory.ooniprobe.fragment.measurement;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.openobservatory.ooniprobe.R;
import org.openobservatory.ooniprobe.model.database.Measurement;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;

public class HttpHeaderFieldManipulationFragment extends Fragment {
	public static final String MEASUREMENT = "measurement";
	@BindView(R.id.title) TextView title;
	@BindView(R.id.desc1) TextView desc1;
	@BindView(R.id.desc2) TextView desc2;

	public static HttpHeaderFieldManipulationFragment newInstance(Measurement measurement) {
		Bundle args = new Bundle();
		args.putSerializable(MEASUREMENT, measurement);
		HttpHeaderFieldManipulationFragment fragment = new HttpHeaderFieldManipulationFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Nullable @Override public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
		Measurement measurement = (Measurement) getArguments().getSerializable(MEASUREMENT);
		assert measurement != null;
		View v = inflater.inflate(R.layout.fragment_measurement_httpheaderfieldmanipulation, container, false);
		ButterKnife.bind(this, v);
		if (measurement.is_anomaly) {
			title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.question_mark, 0, 0);
			title.setTextColor(ContextCompat.getColor(getActivity(), R.color.color_red8));
			title.setText(R.string.TestResults_Details_Middleboxes_HTTPHeaderFieldManipulation_Found_Hero_Title);
			desc1.setText(R.string.TestResults_Details_Middleboxes_HTTPHeaderFieldManipulation_Found_Content_Paragraph);
			desc2.setText(R.string.TestResults_Details_Middleboxes_HTTPHeaderFieldManipulation_Found_Content_Paragraph);
		} else {
			title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.tick, 0, 0);
			title.setTextColor(ContextCompat.getColor(getActivity(), R.color.color_green8));
			title.setText(R.string.TestResults_Details_Middleboxes_HTTPHeaderFieldManipulation_NotFound_Hero_Title);
			desc1.setText(R.string.TestResults_Details_Middleboxes_HTTPHeaderFieldManipulation_NotFound_Content_Paragraph);
		}
		return v;
	}
}
